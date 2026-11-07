# MiniDictJson optimization verification

Changed files: `src/main/java/openccjava/MiniDictJson.java`,
`src/test/java/openccjava/MiniDictJsonTest.java`, `bench/MiniDictJsonBench.java`,
`bench/run-minidict.py`, and this report (paths relative to `openccjava`).

## Implementation and compatibility

- Unescaped strings return a source substring; a builder is allocated only at
  the first escape and receives the complete prefix. All existing JSON escapes
  and literal/escaped UTF-16 code units remain supported, without normalization
  or additional surrogate-pair validation.
- Reject raw U+0000–U+001F in both string paths. ASCII-only integer accumulation
  avoids temporary strings and detects overflow before arithmetic; leading zeros
  remain accepted. Schema readers reject decimals and negative/non-ASCII numbers.
- InputStream loading starts with 8,192 characters rather than 1,048,576. UTF-8
  decoding and closing the supplied stream on success/read/parse failure remain
  unchanged. Path loading already uses a single readAllBytes/decode operation;
  no additional buffering change was justified. No streaming parser was added.
- Javadocs explain allocation, cursor advancement, UTF-16 handling, leading-zero
  compatibility, stream ownership, and the existing BOM rejection policy.
- Keep both levels of LinkedHashMap. DictEntry retains the supplied map;
  hydrate retains entries. StarterUnion aggregates keys independently of order,
  and SingleCodePointIndex builds lookup indexes without requiring inner map
  order. However, writeField explicitly supports unsorted insertion-order
  serialization, and the public dict map exposes iteration order. HashMap would
  change that behavior, so it was rejected without a benchmark or production change.
- BOM support remains a separate, unimplemented format expansion. No metadata,
  schema, API, duplicate-key last-wins, or conversion-engine changes were made.

## Verification

- `./gradlew.bat test --offline`: successful across the project.
- `./gradlew.bat :openccjava:test :openccjava:javadoc --offline`: successful.
- After adding explicit serialization-order and read-failure tests,
  `./gradlew.bat :openccjava:test --offline`: successful again.
- Final test reports: library 189 tests (188 passed, one existing skip), CLI 11
  passed; WASM has no tests. Eight new parser test methods cover normal, malformed,
  Unicode, escape, integer, metadata, order, duplicate, stream, and large inputs.
- Existing DictionaryMaxlengthNoDepsTest verifies fromJsonNoDeps via Path and
  InputStream, the real classpath snapshot, and serialization round trips.
- Benchmark compilation uses `javac --release 8` for all production sources and
  both parser variants. This verifies Java 8 language/API compilation; execution
  was on JDK 21, not an installed Java 8 runtime.
- Original/optimized real snapshot comparison matched dictionary names, full
  mapping contents, iteration order, maxLength and minLength: 25 dictionaries,
  90,146 mappings. `git diff --check` passed.

## Reproducible benchmark

From the repository root:

```text
python openccjava/bench/run-minidict.py cc751b32f0f058175ca4a17463b3a0dbcf710cbb
```

Requires Python 3, git, and JDK 9+; adds no Java dependencies. The script extracts
and renames the baseline class in the ignored build directory and compiles it
alongside the working parser. Use the explicit revision above after committing.

Environment: Windows, Oracle HotSpot 21.0.12.1+1-LTS-4, fixed 256 MiB initial/max
heap. Input: bundled UTF-8 snapshot, 2,534,064 bytes; SHA-256: e9274ae1c0ad41e63eff45280a8d642f2b399e1097118d0c9c1db0724e32caa1.
Each mode/variant uses seven fresh JVMs, alternating execution order. Each JVM
measures its first parse, then 30 warmups and 31 measured parses. Reported cold
values are medians of seven first-parse timings; warm/allocation values are
medians of seven per-JVM medians. Source loading/initial UTF-8 decode and process
launch are excluded; stream mode includes its own buffering and UTF-8 decoding.
Results are a local lightweight comparison, not a general throughput guarantee.

| Mode | Measurement | Original | Optimized |
| --- | --- | ---: | ---: |
| String | Cold parse (ms) | 46.113 | 37.021 |
| String | Warm parse (ms) | 18.600 | 10.506 |
| String | Allocated bytes/parse | 41,764,096 | 18,686,640 |
| Stream | Cold parse (ms) | 73.463 | 61.558 |
| Stream | Warm parse (ms) | 22.438 | 16.682 |
| Stream | Allocated bytes/parse | 52,558,448 | 30,523,520 |

Allocation is measured with HotSpot's per-thread cumulative allocation counter,
including discarded objects and a small reflection measurement overhead. It is
not retained heap or peak memory; those were not measured. Unsupported JVMs
report -1 for allocation. Large-input builder growth adds roughly 1.04 MB more
loading allocation than the old oversized initial buffer in this run; avoiding
the fixed million-character reservation for small inputs is the deliberate tradeoff.
Warm string time decreased about 44% and allocated bytes about 55%; stream time
about 26% and allocated bytes about 42%. Cold results are noisier (individual
string original 41.488–51.148 ms, optimized 35.712–46.949 ms; stream original
62.787–83.860 ms, optimized 57.298–72.222 ms).

Raw per-fork results from this run are in `build/minidict-benchmark.log`; rerunning
the script prints every sample summary. No README, changelog, version, dependency,
or unrelated source files were changed, and no commit was created.
