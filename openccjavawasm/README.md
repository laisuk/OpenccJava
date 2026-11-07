# Experimental TeaVM browser proof of concept

This module compiles the existing Java engine through a project dependency. It
reuses the local Opencc-Fmmseg demo's HTML/CSS and browser-side utilities with
the Java engine in place of Rust. It does not publish a library. Core and CLI sources,
dictionaries, Java 8 compatibility, and release workflows are unchanged.

## Requirements and build

Use JDK 17 or 21 and the repository's Gradle 8.11.1 wrapper. This module targets
Java 17 because TeaVM 0.16.0 requires it; the core continues to target Java 8.
TeaVM 0.16.0 is the current stable plugin as of November 1, 2026.

From the repository root (use `gradlew.bat` on Windows):

```sh
./gradlew :openccjava:test :openccjavacli:test
./gradlew :openccjavawasm:browserJavaScript
./gradlew :openccjavawasm:browserWasmGC
python -m http.server 8080 --directory openccjavawasm/build/generated/teavm
```

Open <http://localhost:8080/?backend=js> or
<http://localhost:8080/?backend=wasm>. Serve over HTTP rather than opening the
HTML file directly. There is no conversion backend and no runtime dictionary
download. The Wasm loader fetches only its compiled Wasm artifact. The default
URL prefers Wasm GC. If Wasm loading or Java initialization fails, the adapter
tries JavaScript and displays both the actual backend and the fallback reason.
An explicit `?backend=js` loads only JavaScript; `?backend=wasm` permits the same
clearly reported fallback. Unknown backend values report an error.

### iPhone Safari development note

Use the default Wasm GC backend on iPhone Safari. On the tested iPhone, the
same TeaVM JavaScript build initialized over LAN HTTP on ports 8080 and 8443,
but Safari reloaded the page during `initialize()` over LAN HTTPS and GitHub
Pages. Module import, configuration enumeration, and embedded resource
decoding passed over HTTPS. The last saved checkpoint varied between UTF-8
buffering and construction of the `st_phrases` map. The cause of the HTTPS
failure remains unresolved.

## Frontend

`index.html` preserves the reference layout from
`D:\codes\Rust\opencc-fmmseg-wasm\index.html`; the adapted event handlers live in
`app.js`. Two CodeMirror editors retain line numbers, history, wrapping, and a
read-only output. The UI includes all 24 configurations and their descriptions,
live Unicode code-point counts, conversion timing/throughput, punctuation,
clipboard paste/copy, clear, UTF-8 output download, TXT/MD opening, encoding
selection/detection, GB mojibake repair, normalization, DeTofu, and Chinese detection.

The original `cjkEncodingDetector.js` is copied unchanged. TextDecoder handles
UTF-8, UTF-16 LE/BE, GB18030/GBK, Big5, and Shift-JIS. Opening files auto-detects
encoding; changing the selector re-decodes the original bytes. The drop area
converts TXT/MD using the selected encoding and offers the converted file for
download. The document picker/drop area also converts DOCX, XLSX, PPTX, ODT,
ODS, ODP, and EPUB through the Java `OfficeHelper` in-memory API. File data
stays in the browser. PDF and other unlisted formats are rejected.

As in the reference, CodeMirror modules load from `esm.sh`; internet access is
needed for a cold load of the editor UI. File contents are never sent to that
CDN. A dependency-load failure is reported by `bootstrap.js`. The independent
`smoke.html` page has no CDN dependency. Clipboard access requires a secure
context (HTTPS or localhost) and browser permission.

The reused frontend's MIT notice is included in `LICENSE-frontend.txt`; the
encoding detector retains its original upstream model attribution in its header.

## Bridge and smoke checks

`ConversionBridge` uses TeaVM's supported `@JSExport` API. JavaScript imports
the ES module; Wasm GC uses `TeaVM.wasmGC.load(...).exports`.

- `initialize()` returns success and initializes a shared engine.
- `convert(text, config, punctuation)` returns text, or `null` on failure.
- `getLastError()` reports the last initialization/conversion error as a string.
- `getSupportedConfigs()` and `isValidConfig(config)` use `OpenccConfig`, covering
  all 24 current configurations rather than a frontend-maintained list.
- `normalizeCompatExtended(text)`, `detofu(text)`, and `zhoCheck(text)` invoke the
  corresponding public Java APIs. Detection returns 0/1/2; -1 indicates failure.

`openccJavaAdapter.js` isolates loading, initialization, backend selection, and
interop. Its string operations throw JavaScript errors when the Java bridge
returns a failure sentinel, preserving the Java error message. The UI reports
these errors in its status area.

Initialization is explicit and synchronous; the first call can block the UI.
The independent `smoke.html` page automatically checks the requested
`汉字转换测试` round trip, phrase disambiguation (`头发发展` ↔ `頭髮發展`), a
10,000+ character segmented input, punctuation in both directions, and
invalid-configuration error reporting, plus Taiwan phrases, Shinjitai, Seal
conversion, all configuration dispatches, normalization, DeTofu, and detection.
Success is recorded as `data-smoke="passed"` on the document element. The
button also permits manual input and punctuation conversion.

## Office/EPUB conversion and byte transfer

`convertOfficeBytes(input, format, config, punctuation, keepFont)` in the adapter
accepts `Uint8Array` (including offset views) or `ArrayBuffer` and returns an
independently owned `Uint8Array`. The bridge exports an ordinary Java `byte[]`
parameter and result through supported TeaVM JSO/`@JSExport` marshalling. The
adapter provides an `Int8Array` view with the correct byte offset and length;
JSO transfers it to Java in one application-level call. The result is exposed
as a typed array and copied to an owned unsigned view. There are no per-byte
application calls, base64 payloads, or assumptions about a Wasm GC array's
internal layout. JavaScript and Wasm GC use the same signatures; TeaVM handles
their different heap representations and copies. Both paths are browser-tested
with binary data covering byte values 0–255 and nonzero input offsets.

The Java bridge calls only:

```java
OfficeHelper.convert(input, format, engine, punctuation, keepFont)
```

`MemoryResult.success`, `message`, and `data` are checked before returning any
bytes; `data` is defensively copied by the core result constructor. Exceptions
or unsuccessful results become `null` plus `getLastError()` at the Java
boundary, then an exception in the adapter. The UI clears any previous download
before conversion and creates a Blob URL only after success and ZIP validation.
The download keeps the extension and adds `_converted` to the basename, with
the format's MIME type. Selection and drag/drop use the same implementation.

The checked-by-default font option forwards `keepFont` unchanged. It protects
the font declarations recognized by the original helper; it does not embed
fonts, install them, or guarantee availability on the receiving device. The
chosen configuration and punctuation flag also reach the original helper.
Entry selection, XML/XHTML conversion, XLSX inline strings, font masking,
unrelated entries, and EPUB reconstruction remain entirely Java core behavior.
No second Office converter, filesystem emulation, or ZIP JavaScript dependency
is introduced.

Busy state disables document/configuration controls and is rendered before
invoking Java. This is a rendering opportunity, **not non-blocking conversion**:
the synchronous Java operation still pauses the main thread. There are no
workers or granular progress updates.

## ZIP findings and package boundaries

TeaVM 0.16.0 supplies working `ZipInputStream`, `ZipOutputStream`, `ZipEntry`,
`CRC32`, byte-array streams, and UTF-8 support on both backends. Its Deflater,
Inflater, and CRC implementations use JZlib. A real compressed ZIP probe (`roundTripZip`) rebuilt UTF-8 text/entry names
and all-byte binary data in each
browser backend. Python independently opened the resulting central directories,
inflated every entry, checked CRCs, and compared names and payloads.

The probe revealed a specific TeaVM defect: `TZipOutputStream` encodes names as
UTF-8 but writes general-purpose flags of 0 (STORED) or 8 (DEFLATED), omitting
bit 11. Independent readers then interpret non-ASCII names as CP437.
`BrowserZip` repairs only that bit in rebuilt local and central headers. It
also validates ZIP32 directory bounds/counts, local-header agreement, duplicate
names, compression, inflated lengths, and CRCs against the central directory
before returning a package. It does not transform XML, choose Office entries,
replace the Java ZIP implementation, or change the existing compatibility shim.

Supported packages use STORED/DEFLATED ZIP32 entries. ZIP64, split archives,
encryption, invalid UTF-8 names, and non-ASCII input names lacking the UTF-8
flag are rejected. Input **and output** are limited to 64 MiB; actual total
inflated data is limited to 256 MiB. Validation performs additional inflation
passes for correctness. Compressed input/output, Java/JS transfer copies,
`MemoryResult`'s copy, the largest materialized XML entry, conversion data, and
Blob storage can coexist; these limits do not guarantee enough memory on every
device. Large files may run slowly or exhaust browser memory.

An independently reopened EPUB fixture exposed a pre-existing core limitation:
the XML-wide conversion can change non-ASCII `href` paths while ZIP entry names
stay unchanged. This module rejects EPUBs with non-ASCII internal entry names
before conversion instead of offering a broken download. ASCII-path EPUBs with
Chinese content are supported. Removing that restriction requires a separately
reviewed core strategy for preserving package references. Core sources remain
unchanged. Numeric XML character references are another inherited limitation:
the helper converts literal text rather than decoding entity-encoded Chinese
before conversion. Fixtures use equivalent literal UTF-8 XML for meaningful
conversion checks. As with the JVM helper, this is not a complete XML-aware
document transformation or application/schema validator.

EPUB outputs are checked to have `mimetype` first, STORED, with the exact
`application/epub+zip` content. Binary images and unrelated entries are preserved.

## Dictionary and parallel compatibility

`DictionaryResources` is a build-time `ResourcesPolicy`, registered by TeaVM's
annotation processor. It embeds the complete, unchanged
`dicts/dictionary_maxlength.json` from the core dependency in each compiled
artifact. No sample dictionaries or hardcoded conversion mappings are used.
It also embeds `CJK_Compatibility_Ideographs.txt`, `Unicode_Compatibility.txt`,
and `CharactersTofu.txt` for the optional Unicode operations. These unchanged
resources use the original core loaders; browser checks assert non-identity
results so that a silently missing optional table cannot pass validation.

Normalization applies Java's additional Unicode compatibility mappings followed
by CJK compatibility ideograph mappings. DeTofu maps the reference's Rust
`DetofuLevelWasm.ExtB` to Java `DeTofu.Level.ExtB`: both mean extension B and
later blocks, replacing mapped characters and preserving unmapped ones. The
tables remain those shipped with the Java engine, rather than importing Rust
data. Supplementary ideographs and emoji are processed as Unicode code points.

The original `OpenCC.DictionaryHolder` first checks a filesystem path, then uses
`DictionaryMaxlength.class.getResourceAsStream(...)` and `fromJsonNoDeps(...)`.
Both browser backends successfully exercise that original loader without modification.
The resource policy explicitly retains the consolidated JSON so that dead-code
elimination cannot omit it.

The first JavaScript compilation failed on these exact missing methods:

```text
java.util.logging.Logger.setLevel(Ljava/util/logging/Level;)V
java.util.stream.IntStream.parallel()Ljava/util/stream/IntStream;
```

`BrowserCompatibility` is a build-time TeaVM plugin confined to this module. It
adds two missing methods to TeaVM's emulated JDK model: a no-op `Logger.setLevel`
and a covariant `IntStream.parallel()` default method returning the receiver.
The latter declaration is missing from TeaVM's `TIntStream`, even though its
implementation supports sequential `parallel()`. This shim patches neither core
sources nor the desktop JAR, and never changes conversion instructions or data.
Log-level configuration is ignored in the browser; diagnostic logs can appear
in the console. Reassess these two shims when upgrading TeaVM.

In TeaVM 0.16.0's class library, `Runtime.availableProcessors()` delegates to
`Navigator.hardwareConcurrency()`. `TSimpleIntStreamImpl.parallel()` returns
`this`, and `isParallel()` returns false. Thus even when the engine's size and
processor guard selects its stream branch, conversion remains sequential.
Desktop parallel behavior is unchanged. There are no workers or browser threads.

## Browser requirements and limitations

The JavaScript page needs ES modules, dynamic import, and modern JavaScript.
Wasm GC additionally requires WebAssembly GC support and TeaVM's matching
generated runtime loader; use an up-to-date Chrome, Edge, Firefox, or Safari.
Older browsers with only basic WebAssembly support cannot run this target.
No shared memory or cross-origin isolation headers are required by this module.

This is a synchronous, in-memory text and document experiment. PDF conversion,
custom dictionaries, JavaFX, browser workers, and deployment are out of scope.

## Validation results

Verified November 1, 2026, using Oracle JDK 21.0.12.1, Gradle 8.11.1,
TeaVM 0.16.0, and headless Chrome 156.0.8078.76 on Windows:

| Check                            | Result                                                                                                                            |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Existing core and CLI test tasks | Pass (186 tests reported, 1 skipped, 0 failures/errors)                                                                           |
| JavaScript compilation           | Pass with module-only JDK emulation shim                                                                                          |
| JavaScript browser execution     | Pass: 11 golden conversion cases, all 24 configs, Unicode operations, errors                                                      |
| Wasm GC compilation              | Pass with the same shim                                                                                                           |
| Wasm GC browser execution        | Pass: same engine checks                                                                                                          |
| Frontend integration             | 72 checks pass per backend, including document selection/drop, busy state, font options, six encodings, failures, and downloads   |
| Office bridge                    | 63 checks per backend; 29 real Blob packages each (7 formats × 4 font/punctuation combinations, plus regional DOCX)               |
| ZIP/document integrity           | Every returned entry matches JVM content; Python checks central directory, decompression, CRC, names, XML, and preserved binaries |
| Independent readers              | python-docx, openpyxl, python-pptx, and odfpy reopen outputs; EPUB container/OPF paths and mimetype are checked independently     |
| Actual saved downloads           | Chrome-saved DOCX and EPUB bytes equal UI Blobs and JVM package content on each backend                                           |
| Clipboard                        | Handler behavior verified with mocked browser permission; OS permission prompts untested                                          |
| Layout                           | Desktop (1280 px) and mobile (390 px) screenshots inspected                                                                       |

Browser checks use a dependency-free Node runner controlling headless Chrome
through DevTools with real rendering frames. To reproduce, build the test
harness, start the static server above, and run from the repository root:

```sh
./gradlew :openccjavawasm:browserIntegration
node openccjavawasm/src/test/run-browser.mjs "C:/Program Files/Google/Chrome/Application/chrome.exe"
python openccjavawasm/src/test/validate-office-packages.py
```

The runner requires Node 22+ (built-in WebSocket), accepts an optional server URL
as its second argument, and stores DOM evidence/screenshots/browser profiles in
`build/browser-validation`. The test harness is available at
`/integration.html?backend=js` and `/integration.html?backend=wasm` after the
verification task. It is not copied by the normal frontend build tasks.
The runner also blocks Wasm backend assets and verifies the UI reports
JavaScript fallback. Other browsers have not been tested.

Real fixtures live in `src/test/fixtures`: DOCX and PPTX include an image, XLSX
includes an inline string plus unchanged numeric/formula cells, ODT/ODS/ODP
are produced by odfpy, and EPUB contains a container, OPF, navigation, chapter,
and image. They include `头发发展，计算机软件，汉字转换测试。`, curly quotes, emoji,
and Chinese font names. Negative fixtures include damaged compressed DOCX data
and the non-ASCII EPUB path case. Truncation, invalid ZIP, unsupported PDF, and
invalid configuration are also tested. `officeReferences` generates 29 expected
packages using the JVM's original in-memory API; `browserIntegration` runs it
and copies the fixtures/references. ZIP binary equality is not required, but
every entry's uncompressed content is compared exactly.

For independent Python reader validation (and optional fixture regeneration),
install test-only tools into the ignored build directory:

```sh
python -m pip install --target openccjavawasm/build/test-python python-docx==1.2.0 openpyxl==3.1.5 python-pptx==1.0.2 odfpy==1.4.1
# Optional: regenerate checked-in fixtures, then rebuild browserIntegration.
python openccjavawasm/src/test/generate-office-fixtures.py
```

These tools are never application dependencies. The runner stores ZIP evidence,
all converted packages, and real downloads under `build/browser-validation`.
`zip.html` and `office.html` test the runtime without CodeMirror/CDN access.
Desktop Office/LibreOffice application rendering has not been verified; the
independent package/document reader checks do not establish visual fidelity.

Generated sizes (uncompressed bytes, default TeaVM optimization/obfuscation,
complete dictionary embedded in each target):

| Artifact                             |     Bytes |
|--------------------------------------|----------:|
| `js/openccjava.js`                   | 3,862,753 |
| `wasm-gc/openccjava.wasm`            | 3,319,519 |
| `wasm-gc/openccjava.wasm-runtime.js` |    17,268 |

There are no remaining blockers for these two smoke tests. This does not
establish compatibility of every OpenCC configuration, Java API, or browser.
TeaVM's annotation processor emits a source-version warning under Java 17;
it still generates and loads the dictionary resource policy successfully.

## Office integration files

- Runtime: `ConversionBridge.java`, new `BrowserZip.java`,
  `openccJavaAdapter.js`, `app.js`, and `index.html`.
- Verification: `build.gradle.kts`, `src/test/java/openccjavawasm/OfficeReference.java`,
  `src/test/fixtures/`, `src/test/generate-office-fixtures.py`,
  `src/test/validate-office-packages.py`, `src/test/run-browser.mjs`, and
  `src/test/web/{zip.html,zip-fixture.json,office.html,office.js,integration.js}`.
- Documentation: this README. Core/CLI sources, dictionary data, and the
  existing TeaVM compatibility shim were not modified for Office support.

## Dense-font EPUB performance investigation

See [EPUB-HANG-INVESTIGATION.md](EPUB-HANG-INVESTIGATION.md) for the actual-book
reproduction, phase timings, cursor audit, entity-preservation tests, and proposed
core fix. The apparent hang is repeated whole-document font-marker restoration,
not entity parsing. No core fix has been applied yet. The browser suite includes
16 additional entity/dense-font package cases per backend and verifies their
contents against JVM references. Diagnostics are opt-in and live outside the
stable core. Artifact sizes above include the temporary diagnostic exports.

## References

- [TeaVM Gradle configuration](https://teavm.org/docs/tooling/gradle.html)
- [Supported JavaScript exports](https://teavm.org/docs/runtime/js-modules.html)
- [JSO primitive-array marshalling](https://teavm.org/docs/runtime/jso.html)
- [Resource policy SPI](https://teavm.org/docs/runtime/compiler-extensions.html)
- [Wasm GC loader](https://teavm.org/docs/wasm-gc-backend/loader.html)
- [TeaVM 0.16.0 release](https://teavm.org/docs/release-notes/0.16.0.html)
