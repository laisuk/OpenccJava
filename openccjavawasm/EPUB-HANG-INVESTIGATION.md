# EPUB apparent hang: investigation and proposed fix

Investigated November 1–2, 2026, with TeaVM 0.16.0, Oracle JDK 21,
Gradle 8.11.1 and headless Chrome 156 on Windows. No stable core code or
conversion behavior was changed in this investigation.

## Finding

The actual `解忧杂货店.epub` reproduces a long synchronous main-thread stall,
not an infinite loop. The dominant cost is the original Java OfficeHelper's
font-placeholder restoration in `convertTextEntry`, amplified by TeaVM's
character-by-character `String.replace(CharSequence, CharSequence)` emulation.
The entity content is not the cause.

`OfficeHelper.java:632–634` creates a distinct `__F_O_N_T_n__` marker for **every
font match**, even repeated values. At lines 661–663 it then calls
`converted.replace(marker, font)` once for every map entry. Each replacement
rescans and rebuilds the whole fragment: work grows approximately as the
number of matches multiplied by fragment length. With increasing font density
and document size together, this is quadratic work and substantial allocation.

The book contains 9,866 matching font declarations, but each affected chapter
uses only two or three distinct captured values:

| Entry | Font matches | Distinct values | Ampersands |
| --- | ---: | ---: | ---: |
| `OEBPS/Text/part0002.xhtml` | 1,766 | 3 | 41 |
| `OEBPS/Text/part0003.xhtml` | 1,598 | 3 | 33 |
| `OEBPS/Text/part0004.xhtml` | 1,604 | 3 | 25 |
| `OEBPS/Text/part0005.xhtml` | 2,306 | 3 | 41 |
| `OEBPS/Text/part0006.xhtml` | 2,592 | 2 | **0** |

The last, slow chapter has no entities at all. Other chapters contain `&nbsp;`,
`&quot;`, and `&#160;`. None is decoded or scanned specially by this EPUB path.

## Actual book measurements

| Runtime / operation | Font preservation off | Font preservation on |
| --- | ---: | ---: |
| JVM original OfficeHelper/OpenCC | 86.9 ms | 2,013 ms |
| TeaVM JavaScript diagnostic pipeline with OpenCC | 483 ms | 41,222 ms |
| TeaVM Wasm GC diagnostic pipeline with OpenCC | 245 ms | 17,118 ms |
| TeaVM JavaScript with **identity callback** | — | 31,410 ms |
| TeaVM Wasm GC with **identity callback** | — | 15,910 ms |

These are individual diagnostic runs, not statistically controlled benchmarks.
Browser runs include module-local ZIP validation and an extra entry inspection
pass; JVM numbers in the first row time the original helper only. The extra
passes are small relative to the font-restoration cost. Warm-up and contention
can change absolute timings. All listed operations completed successfully.

Replacing OpenCC with an identity callback is diagnostic isolation only: it
still reproduces the stall without text conversion. The normal conversion path
is unchanged and always uses the selected OpenCC engine/configuration.

## Phase and size evidence

- The input ZIP independently passes Python CRC validation: 459,009 compressed
  bytes, 16 entries, and 1,761,210 total expanded bytes.
- The largest XHTML entry is 414,821 UTF-8 bytes (322,111 UTF-16 code units for
  this BMP chapter). Neither the 64 MiB package limit nor the 256 MiB actual
  expansion limit is approached.
- JVM per-entry inflation is roughly 0.01–1.3 ms; direct OpenCC conversion of
  each large original XHTML fragment is roughly 6–50 ms in this run.
- Browser console traces identify every input entry and bracket input ZIP
  validation, entry inflation, each text-converter callback, helper completion,
  output validation, and the JavaScript export call.
- With fonts enabled, callbacks themselves take tens of milliseconds with
  OpenCC, or zero/approximately zero with identity. Multi-second gaps occur
  **after** one callback returns and **before** the next entry's callback.
  The source and font-density reproduction identify restoration as the cause,
  rather than the callback or initial masking. ZIP reconstruction also finishes
  quickly when the same book is processed with fonts disabled.
- The export call and helper/ZIP completion timestamps differ by only a few
  milliseconds. Byte-array marshalling is not the long phase.
- The UI cannot paint while this synchronous work runs, so it looks frozen even
  though the loops eventually terminate. The browser runner now uses a 45-second
  no-response watchdog; the actual font-on runs completed within that bound.

## Cursor/termination audit and regressions

There is no HTML entity parser in `OfficeHelper.convertXmlContent` for EPUB;
the whole fragment is passed to the text converter. Therefore there is no
`&...` cursor to get stuck in this implementation.

The font regex requires a nonempty prefix and captured value, so each successful
`Matcher.find()` advances. OpenCC's split-range `for` loop increments its index
every iteration, and its replacement loop advances by a matched positive
length or the current Unicode code-point width. TeaVM's nonempty-target
`String.replace` loop increments its outer index on mismatch and skips the
matched target length on a hit; its empty-target branch is separate. Those
loops terminate. The defect is how many complete replacement scans are made.

Added eight synthetic EPUB fixtures, each tested with both font settings:
named entities (including unknown names), decimal/hex references (including
invalid scalar values), malformed/unterminated sequences, two 20,000-character
entity-like runs, 2,000 consecutive ampersands, and 128/512/2,048 repeated font
declarations without entities. Malformed content is deliberately treated as
opaque text in these preservation tests, not repaired or parsed as XML.

All **16 packages per backend** match JVM uncompressed entry contents exactly,
pass independent ZIP CRC checks, and preserve the original entity spellings.
No speculative entity decoding, replacement, or removal was introduced.

| Repeated font declarations | JS, fonts off/on | Wasm GC, fonts off/on |
| --- | ---: | ---: |
| 128 | 3.7 / 16.7 ms | 1.9 / 7.8 ms |
| 512 | 6.4 / 192.6 ms | 3.1 / 86.2 ms |
| 2,048 | 19.4 / 2,623.9 ms | 8.2 / 1,415.9 ms |

Entity-only cases complete in milliseconds. The regression suite checks output
and termination and records dense-font timings; it does **not** pretend the
known performance defect is fixed or assert a machine-specific timing budget.
After a fix is approved, these cases provide a before/after scalability test.

All previous checks still pass: 63 Office bridge checks, 72 frontend checks,
11 golden text cases plus all configurations/Unicode checks on each backend,
29 independently validated Office packages per backend, actual DOCX/EPUB
downloads, and existing core/CLI tests (185 passed, one skipped). EPUB mimetype
ordering/STORED rules and all seven enabled document formats are unchanged.

## Proposed minimal core fix — not applied

Replace the `for (fontMap.entrySet()) String.replace(...)` restoration with
**one forward pass** over exact generated marker tokens, using the existing map
for lookup and a single StringBuilder or Matcher appendReplacement/appendTail
sequence. Preserve unknown marker-shaped text and append untouched spans once.
Every scan iteration must advance to the end of a nonempty marker match or
terminate. No HTML entity processing is needed.

This changes only font restoration, remains Java 8 compatible, and avoids
whole-fragment scans for each placeholder. It should retain existing font,
XML, punctuation, and packaging semantics. Tests should compare all Office
formats and both font flags, plus literal marker-like input and replacement
values, before accepting the patch.

Deduplicating identical font values during masking would also greatly help this
book, but still scales poorly for many distinct values. A single restoration
pass addresses the underlying repeated-scan cost. A module-local replacement
of TeaVM String.replace with a native JS operation would only hide part of a
cross-runtime algorithmic issue and leave the JVM cost intact. Duplicating
Office conversion in the module is unnecessary.

Until that core change is reviewed, unchecking **Preserve document font
declarations** is a tested fast workaround. It can change font names and is
therefore an explicit user choice, not an automatic fallback.

## Reproduction and diagnostics

The actual book is copied only to ignored `build/generated/teavm/private-diagnostics/book.epub`.
No book text or copyrighted chapter content is included in source fixtures.

```powershell
.\gradlew.bat :openccjavawasm:epubHangProbe '-PprobeBook=C:\Users\bryan\Documents\Inbox\Books\解忧杂货店.epub'
.\gradlew.bat :openccjavawasm:browserIntegration
python -m http.server 8080 --bind 127.0.0.1 --directory openccjavawasm/build/generated/teavm
# In another shell, after copying the book to the private path above:
$env:EPUB_DIAGNOSTICS = 'epub-diagnostics.html?backend=js&font=true,epub-diagnostics.html?backend=wasm&font=true'
node openccjavawasm/src/test/run-browser.mjs 'C:/Program Files/Google/Chrome/Application/chrome.exe'
Remove-Item Env:EPUB_DIAGNOSTICS
```

`&font=false` and `&identity=true` select isolation cases. The opt-in
`EpubDiagnostics` and bridge diagnostic exports report entry names/counts/times,
not chapter text. Normal UI conversion does not enable these diagnostics.
They can be removed after the approved fix; the synthetic regression cases
and JVM references should remain. Existing browser/JVM output and timing logs
are in the ignored module build directory (`hang-*.log`, `entity-build.log`,
and `browser-validation/`).
