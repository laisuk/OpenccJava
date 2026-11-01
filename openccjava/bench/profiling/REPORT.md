# Direct s2t profiling — 2026-10-05

This is the initial investigation before iterator removal. The benchmark now defaults to whole files, following the user's updated preference. See [STEP1.md](STEP1.md) for the implementation and fresh full-corpus before/after measurements. Use `-p maxChars=999999` to reproduce this report's historical capped results.

The subsequent single-code-point index implementation, throughput comparison, dynamic build cost and retained-memory results are in [STEP2.md](STEP2.md).

The supplied corpora are useful first baselines, but they do not cover every conversion workload. This investigation adds a real-corpus JMH benchmark and records CPU/allocation evidence without modifying production conversion code.

## Environment and measurement

- Windows, Intel Core i5-13400, 10 physical cores / 16 logical processors.
- Oracle HotSpot JDK 21.0.12.1, JMH 1.37; library compiled for Java 8.
- Direct `converter.s2t(input, false)` on a reused converter; file reading, dictionary construction and initial plan preparation occur in trial setup.
- Average time: 3 x 1-second warmup, 5 x 1-second measurement, 2 fresh JVM forks, 1 caller thread. Internal parallel streams still use the common pool.
- Return value is consumed by JMH. Setup checks full output equality with `convert(input, false)`; this is dispatch consistency, not an independent semantic oracle.
- GC profiler reports bytes allocated per operation across threads. No forced `System.gc()` between operations.
- Timings are exploratory local baselines, not release guarantees. Extend iteration duration and fork count before accepting an optimization.

## Corpus scope

| Input | UTF-16 units measured | Unicode code points | Notes |
| --- | ---: | ---: | --- |
| SanWenHansSeal.txt | 7,340 | 7,331 | Whole file; includes supplementary characters; 1,150 segments |
| sample.txt prefix | 999,999 | 999,999 | Strictly below one million; prefix capped without splitting a surrogate pair |

The full sample actually contains **1,130,220 UTF-16 units**, rather than fewer than one million. Initial uncapped results remain in `default.json`; the final baseline is `capped-default.json`.

Input SHA-256 (original bytes):

- SanWenHansSeal.txt: `9333FBD9CE45E95F33A62D1F108478ACA5E50F073CD7072C748C167FBEA6A57C`
- sample.txt: `431A1E9FF0264977A16D21F7012AD5D24F6ED4E481E5CD82CA2A48524B7EA308`

## Results

| Corpus | Default ms/op (JMH 99.9% error) | Million UTF-16 units/sec | Allocated bytes/op | Bytes/input unit | Pool parallelism=3 ms/op |
| --- | ---: | ---: | ---: | ---: | ---: |
| Short | 0.195 ± 0.010 | 37.7 | 1,743,872 | 237.6 | 0.299 ± 0.021 |
| Long prefix | 24.947 ± 0.548 | 40.1 | 255,679,232 | 255.7 | 42.225 ± 1.214 |

Throughput is derived from average time, not a separately measured throughput mode. Errors are rounded; raw JSON retains precision. Default uses about 15 common-pool workers plus caller participation. Reduced pool parallelism is slower for both corpora on this host, with nearly identical allocation per operation.

The short corpus also enters the parallel branch: its 1,150 segments exceed the 1,000-segment threshold even though its length is below 10,100. It therefore does **not** establish the latency of truly short sequential inputs.

An initial `-XX:ActiveProcessorCount=1 -XX:+UseG1GC` diagnostic on the uncapped corpora measured 0.857 ms/op short and 161.230 ms/op full sample. This selects the sequential branch but also changes JVM/compiler/GC ergonomics. Do not interpret its ratio as an isolated parallel speedup or compare its full sample directly to the capped baseline.

## Profile evidence

JFR startup recordings use the `profile` settings and `DebugNonSafepoints`. They cover setup, warmup and measurement; `JfrSummary.java` filters CPU/allocation stacks to the segmentation/conversion body, excluding dictionary loading and plan construction. These samples describe conversion work, not a measurement-only latency estimate. Instrumented timings are excluded from the baseline above.

The attach-based JMH JFR profiler reported output paths but created no files. A startup recording exposed an unwritable default JFR repository at `R:\Temp`. Explicitly setting the repository inside `openccjava/build` fixed collection. `short.jfr` and `long.jfr` are saved locally and ignored by Git.

| Allocation site | Short sampled weight | Long sampled weight |
| --- | ---: | ---: |
| Phrase substring: String + byte array, OpenCC.java:1328 | 48.2% | 44.1% |
| Phrase dictionary iterator, OpenCC.java:1329 | 21.4% | 21.6% |
| Single substring: String + byte array, OpenCC.java:1347 | 14.3% | 15.8% |
| Single dictionary iterator, OpenCC.java:1348 | 6.1% | 6.4% |

Percentages are **sampled allocation weights**, not exact object counts or independently measured allocation totals. The GC profiler is the source for bytes/op. Allocation stack locations can be affected by inlining/sampling.

CPU analysis collected 514 short and 2,456 long conversion samples. The JDK `hot-methods` view for the long recording puts `HashMap.getNode` at 39.66% of all Java execution samples, `convertSegmentWithUnion` at 20.32%, and `StringUTF16.compress` at 5.50%. These are leaf/self samples, not inclusive wall-time percentages. Segmentation, substring construction, hash lookup and dictionary iteration are visible; startup cache rebuilding is not the main warmed-body target.

## Improvement order

1. **Remove dictionary-loop iterators.** Replace the enhanced-for loops inside `convertSegmentWithUnion` with indexed loops or immutable arrays prepared with the partition. The sampled iterator allocation is about 28% on both corpora. Preserve dictionary order and first-hit behavior. Test this small change independently before changing the lookup algorithm; reduced allocation does not guarantee proportional latency improvement.
2. **Avoid allocating a String for single-code-point lookup.** Prepare an immutable BMP replacement table and a supplementary-code-point fallback per partition, respecting dictionary precedence. Account for added memory and custom dictionaries. This targets about 14–16% of sampled allocation. Keep valid surrogate pairs and isolated surrogate behavior consistent.
3. **Prototype substring-free phrase matching.** A trie or immutable slice-aware lookup index can search the original input without constructing each candidate String. Preserve longest-first matching, dictionary precedence, phrase boundaries and UTF-16 key-length semantics. Compare build time, retained memory and cold-start cost as well as warmed latency. A trie is a candidate, not a measured winner.
4. **Reduce segment materialization.** Pass original-string start/end offsets into the converter; sequential conversion can append into one output builder, while parallel conversion can use ordered coarse chunks. This removes input-segment copies and can reduce range/result objects. Never allow phrases to match across existing delimiter boundaries. This is a secondary target after the measured lookup allocations.
5. **Tune scheduling using more workloads.** Sweep lengths near 10,100 and segment counts near 1,000, including punctuation-free input and many tiny segments. Compare normal processor count with an explicit benchmark-only sequential strategy to avoid the ActiveProcessorCount confound. Consider coarse task batching and a supported caller policy for concurrent/server environments. The present measurements do not justify reducing default parallelism.
6. **Micro-tune masks/cache only after larger targets.** Iterate supported phrase-length bits rather than all candidate lengths if profiling supports it; retain handling of custom keys >=64 units. Consider preallocated plan keys only if tiny-input profiles show cache overhead. Avoid spending effort on warmed dictionary loading.

## Broader package validation

- Add real tiny inputs (10, 100, 1,000 units), mixed ASCII/CJK, no-op Latin text, supplementary characters, ambiguous phrases, varied punctuation density, and multiple excerpts rather than only one repeated/prefix corpus.
- Separate cold first conversion/dictionary preparation from reused-converter conversion. A fresh converter may still reuse shared dictionary/plan caches; use fresh JVMs for actual cold starts.
- Add `t2s`, `s2twp`, punctuation enabled, and custom dictionary cases before generalizing s2t results to the package.
- Test one and multiple caller threads, with one converter per JMH thread, to expose common-pool contention and throughput/tail-latency tradeoffs.
- For algorithm changes, compare full outputs against the unchanged baseline across corpora and configurations, plus focused phrase precedence, delimiter, surrogate and long-custom-key regression cases. Use an independent OpenCC reference where semantic parity is required.
- Measure retained memory and plan/index construction for lookup-table/trie candidates; repeat on relevant supported JVMs and realistic deployment CPU/heap limits.

Baseline library tests: **127 cases, 126 passed, 1 skipped, zero failures/errors**. JMH build succeeded. Existing user changes to the library JAR, short corpus and other untracked files were preserved.

## Reproduce from repository root (PowerShell)

```powershell
./gradlew.bat :openccjava:jmhJar :openccjava:test --console=plain
java -jar openccjava/build/libs/openccjava-1.5.0-jmh.jar S2tCorpusBenchmark -p maxChars=999999 -wi 3 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff openccjava/bench/profiling/capped-default.json
java -jar openccjava/build/libs/openccjava-1.5.0-jmh.jar S2tCorpusBenchmark -p maxChars=999999 -wi 3 -i 5 -w 1s -r 1s -f 2 -t 1 -jvmArgs '-Djava.util.concurrent.ForkJoinPool.common.parallelism=3' -prof gc -rf json -rff openccjava/bench/profiling/pool3.json
```

To select a different cap, use `-p maxChars=100,1000,10000,999999`. These caps select prefixes; they do not create independent text samples.

For the recording, use the installed JDK and run each corpus separately (one fork per output file):

```powershell
New-Item -ItemType Directory -Force openccjava/build/jfr-repository | Out-Null
$profileRoot = (Resolve-Path openccjava/bench/profiling).Path.Replace('\', '/')
$repositoryRoot = (Resolve-Path openccjava/build/jfr-repository).Path.Replace('\', '/')
java -jar openccjava/build/libs/openccjava-1.5.0-jmh.jar S2tCorpusBenchmark -p corpus=SanWenHansSeal.txt -wi 3 -i 5 -w 1s -r 2s -f 1 -t 1 -jvmArgs "-XX:FlightRecorderOptions=repository=$repositoryRoot -XX:StartFlightRecording=filename=$profileRoot/short.jfr,settings=profile,dumponexit=true -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints"
# Repeat with corpus=sample.txt and filename ending in long.jfr.
java openccjava/bench/profiling/JfrSummary.java openccjava/bench/profiling/short.jfr
java openccjava/bench/profiling/JfrSummary.java openccjava/bench/profiling/long.jfr
& 'C:/Program Files/Java/jdk-21.0.12.1/bin/jfr.exe' view --width 150 hot-methods openccjava/bench/profiling/long.jfr
```

See [OpenJDK JMH](https://github.com/openjdk/jmh) and the [JDK 21 JFR command reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html) for tool behavior. The `JfrSummary.java` source launcher requires a modern JDK and is not part of the Java 8 library.
