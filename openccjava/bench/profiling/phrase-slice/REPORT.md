# Experimental PhraseSliceIndex evaluation — 2026-10-06

**Recommendation: refine and retest; do not adopt this prototype as-is.** Normal-corpus conversion benefits substantially, especially allocation, but deliberate collisions and long isolated lookups regress. Production sources, README and CHANGELOG were not changed.

## Baseline and isolation

Baseline commit: `6863a2034d10f79476279e9402c3be86fd889048`, including the single-code-point index and StarterUnion long-key fix. Production `OpenCC.java` SHA-256 before and after: `DD65092BFD09CFAC47DF8F39846A15D2BF22A184399A02D3A02A383F7633FB70`. `git diff --stat` remains empty for tracked files.

The experiment resides in test/JMH source sets and analysis files. It does not enter the library's main sources or runtime JAR. `ExperimentalSliceOpenCC` subclasses OpenCC and overrides the segmentation callback. Its segmentation/conversion methods are generated from the current production implementation, with only phrase candidate lookup substituted. The existing single-code-point index, starter checks, exact length masks, >=64-unit caps, dictionary bounds/order, longest-first search, parallel threshold and configuration rounds remain in place.

### Exact index design

- One immutable index per phrase DictEntry, reused across partitions in one experimental converter.
- Open-addressed linear probing; power-of-two capacity with target occupancy <=65%.
- Flat `String[] keys`, `String[] values`, `int[] hashes`; references to authoritative dictionary Strings, no key/value copies.
- String-compatible UTF-16 range hash, computed once per candidate length and reused across eligible dictionaries. Overflow is intentional.
- Probe selection uses `hash ^ (hash >>> 16)`; equality verifies stored hash, key length and case-sensitive `regionMatches` against the input range.
- No candidate String allocation for indexed maps. Null values are misses; empty values remain hits.
- Only exact HashMap, LinkedHashMap and ConcurrentHashMap classes are indexed. Subclasses, identity maps, comparator maps and wrappers retain the original substring + Map.get path. A fallback candidate substring is allocated lazily once and shared between fallback dictionaries for that attempted length.
- Existing maps/schema/custom syntax are untouched. Attached dictionaries retain the existing immutability requirement.

This prototype's cache is converter-local, not a new production dictionary-scoped cache. Therefore a fresh experimental converter rebuilds its index even if the dictionary/plan is shared. Cache sharing and lifecycle integration are explicitly deferred; current preparation results include this conservative cost. One synchronized partition-cache access occurs per conversion round before workers launch; probing itself is immutable and lock-free.

## Environment and methodology

Windows, Intel i5-13400 (16 logical processors), Oracle HotSpot JDK 21.0.12.1, JMH 1.37, Java 8-targeted sources. One caller thread; internal parallel processing stays enabled according to the production thresholds.

Main comparison: average-time JMH, two fresh JVM forks per matcher/size, three 1-second warmup iterations and five 1-second measured iterations per fork, GC profiler. Trial setup loads input, primes each converter and checks equality against production. Returned outputs are consumed by JMH. File reading and index/plan preparation are outside warmed conversion timing. Matcher/size combinations run in separate forks; baseline combinations precede slice combinations, so randomized/interleaved repetitions remain desirable before release decisions.

Both use identical raw UTF-8 corpus bytes. `sample.txt` has **1,130,220 UTF-16 units**, including **21,630 CRs**; removing CRs yields the cited 1,108,590 units. No newline normalization was performed. Its SHA-256 is `431A1E9FF0264977A16D21F7012AD5D24F6ED4E481E5CD82CA2A48524B7EA308`. Short corpus has 7,340 UTF-16 units / 7,331 code points.

## Warmed conversion: matched JMH results

Throughput below is million UTF-16 units/sec, derived from mean latency. Gain is `baselineTime / sliceTime - 1`, not latency reduction. Allocation is bytes/op across participating threads as reported by the GC profiler.

| Input units | Baseline ms/op | Slice ms/op | Baseline → slice throughput | Throughput gain | Baseline → slice bytes/op | Allocation reduction |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 0.00520 | 0.00360 | 19.25 → 27.79 | 44.4% | 20,840 → 7,928 | 62.0% |
| 1,000 | 0.06032 | 0.03753 | 16.58 → 26.64 | 60.7% | 202,074 → 64,412 | 68.1% |
| 10,000 | 0.25155 | 0.20312 | 39.75 → 49.23 | 23.8% | 2,135,121 → 531,616 | 75.1% |
| 100,000 | 2.70354 | 1.61323 | 36.99 → 61.99 | 67.6% | 21,098,298 → 4,998,914 | 76.3% |
| 1,000,000 | 24.14 | 16.27 | 41.43 → 61.48 | 48.4% | 210,149,387 → 50,501,797 | 76.0% |
| Full sample, 1,130,220 | 26.67 | 18.33 | 42.37 → 61.66 | 45.5% | 237,193,877 → 56,692,235 | 76.1% |
| Whole short corpus, 7,340 | 0.17867 | 0.15423 | 41.08 → 47.59 | 15.8% | 1,414,112 → 330,783 | 76.6% |

Every JMH conversion case above is cache-hot: its index and plan were prepared in trial setup. These point estimates are exploratory local results. Raw confidence/error data are retained in `conversion.json` and `comparison.json`; they should take precedence over rounded table values.

The 100,000-unit baseline is particularly noisy (2.704 ± 1.505 ms), so its apparent 67.6% throughput gain is not a precise conclusion. The full-sample intervals separate clearly: baseline 26.674 ± 1.079 ms, slice 18.330 ± 0.768 ms (JMH 99.9% errors).

The existing `S2tCorpusBenchmark` was also run as a production control (one fork, otherwise the same warmup/measurement counts): approximately 0.18 ms short and 26.88 ms full, with allocation matching the main baseline. It supports the baseline's consistency without establishing portability to other hosts.

## Preserved CI-style comparison

`PhraseSliceCiBench` copies the existing OpenccBench methodology: 20 warmup conversions of 10,000 units, 20 measured runs per case, explicit System.gc between runs, ten cache-prime rounds, and a new converter for the final million-unit case. Disk reading is outside the timed conversions. These are separate plain-Java processes, with less controlled JIT warmup than JMH.

| Input | Baseline average ms | Slice average ms | Baseline average M units/sec | Slice average M units/sec |
| --- | ---: | ---: | ---: | ---: |
| 100 | 0.09681 | 0.14372 | 1.151 | 0.723 |
| 1,000 | 0.28478 | 0.27576 | 3.792 | 3.886 |
| 10,000 | 0.52086 | 0.46134 | 19.383 | 21.847 |
| 100,000 | 2.87896 | 2.04715 | 35.722 | 49.612 |
| 1,000,000 | 42.29763 | 25.90963 | 23.969 | 41.924 |
| 1,000,000 cache-hot | 44.69293 | 21.59273 | 22.673 | 46.584 |

The runner averages per-run throughput separately; it is not the reciprocal of mean latency. Its 100-unit case is **48% slower** for the prototype, despite warmed JMH improving that size. This divergence demonstrates sensitivity to JIT maturity, forced-GC/cache conditions and tiny-input timing overhead; the experiment has not isolated those causes. Do not hide this regression or interpret a 20-round mixed-size run as fully warmed JMH evidence.

The final CI-style case has shared dictionaries/plans hot. Because the prototype registry is converter-local, it builds an index on the new converter's first measured call; subsequent calls reuse it. Its label does not mean every prototype index was prebuilt before timing.

## CPU and allocation profiles

Full-sample JFR recordings use profile settings, DebugNonSafepoints, a writable repository under `openccjava/build`, one fork, three 1-second warmups and five 2-second measurements. Startup recordings cover setup, warmup and measurements. The analyzer filters to the selected matcher's segmentation/conversion stacks and excludes the experimental setup's production-reference conversion. Profiled timings are not used as performance baselines.

| CPU leaf/self sample category | Baseline | Slice |
| --- | ---: | ---: |
| HashMap.getNode | 41.50% | Absent from top 15 |
| Main conversion body | 16.90% | 31.21% |
| PhraseSliceIndex.lookup | — | 14.86% |
| PhraseSliceIndex.hashRange | — | 8.34% |
| AbstractStringBuilder.ensureCapacityInternal | 1.38% | 12.80% |

Baseline: 2,248 selected execution samples; prototype: 2,422. Percentages are relative sampled CPU attribution, not inclusive wall time or equal work counts. A remaining site's larger percentage does not imply it became absolutely slower.

Baseline phrase-candidate allocation at OpenCC.java:1334 accounts for **80.58%** of selected sampled allocation weight: backing byte arrays 54.78%, String objects 25.80%. Those candidate sites disappear from the indexed path. Remaining prototype allocation is output Strings/builders, segment copies, range arrays/list growth and parallel-result arrays. Instrumented allocation percentages are weighted samples, not exact bytes/op; normal GC-profiler results establish the ~76% operation-level reduction.

**Why normal conversion improved:** the prototype avoids copying candidate ranges into temporary Strings and their backing arrays, and removes repeated general-purpose map-node traversal. Its flat arrays reduce lookup indirection while sharing existing dictionary Strings. Hashing/comparison still costs CPU; the measurements show that trade pays off for these mostly short-key corpora. This is supported by both allocation and CPU evidence, rather than assuming allocation reduction guarantees speedup.

## Construction, preparation and first conversion

Preparation measurements use loaded dictionaries, two forks, 3/5 x 1-second warmup/measurement and GC profiling. Full-plan cases construct the same fresh production plan/union/single index and a fresh converter in both variants; the slice variant additionally prepares its phrase index.

| Operation | Baseline µs/op | Slice µs/op | Baseline → slice allocated bytes/op |
| --- | ---: | ---: | ---: |
| Standalone index construction | Not applicable | 823.94 ± 39.98 | — → 1,572,950 |
| Fresh plan + converter preparation | 756.03 ± 40.58 | 1,689.33 ± 55.97 | 2,420,668 → 3,994,421 |
| New converter, tiny conversion using already prepared plan | 0.273 ± 0.004 | 879.47 ± 169.16 | 1,016 → 1,573,721 |

The baseline indexOnly method merely returns the map; its sub-nanosecond result is a no-op control, not a comparable construction task. Added full-plan preparation is approximately **0.933 ms**. The tiny new-converter case shows how rebuilding an index can overwhelm conversion savings.

Fresh-JVM first-conversion benchmark: five forks per matcher, zero warmup, one measured 10,000-unit conversion. Input reading is trial setup; dictionary loading, plan/index preparation and first conversion are inside timing, JVM startup outside it. Baseline **86.12 ± 8.09 ms**, slice **92.44 ± 12.36 ms**. Intervals overlap: the point estimate adds 6.32 ms, but this noisy cold measurement does not establish that exact penalty. OS disk caches were not purged.

Arithmetic amortization using the full-plan overhead and warmed mean savings: roughly **585 calls of 100 units**, **20 calls of 10,000 units**, **39 short-corpus calls**, or **one million-unit conversion**. This is a steady-state estimate, not a guarantee for fresh JIT or GC conditions. Dictionary-scoped index sharing could change the new-converter cost substantially, but was not implemented.

## Retained memory

Measured separately with Instrumentation.getObjectSize, summing the index object and its three arrays, excluding dictionary-owned Strings:

- 50,887 S2T phrase mappings; capacity 131,072 slots, actual occupancy about 38.8% after power-of-two rounding.
- **1,572,944 bytes = 1.50 MiB** retained per distinct indexed S2T phrase entry in the prototype.
- Construction allocates approximately the same amount; existing keys/values are referenced without copying.
- Small identity registries and per-partition index-reference arrays are additional, excluded from this index-owned figure.
- Other phrase dictionaries/configurations add their own indexes; sharing within one converter avoids repeating the same entry. Separate converters do not share this prototype registry. Object layout assumes this JVM's compressed references/alignment.

## Isolated lookup findings

One fork per case, two 1-second warmups, three 1-second measurements, GC profiling. The input includes surrounding characters so baseline substring must materialize the candidate. Setup creates all maps/indexes; benchmark input generation does not add allocations.

| Scenario | Baseline ns/op | Slice ns/op | Baseline → slice bytes/op |
| --- | ---: | ---: | ---: |
| BMP hit, 3 units | 15.07 | 11.25 | 72 → approximately 0 |
| BMP miss, 16 units | 20.40 | 12.84 | 104 → approximately 0 |
| BMP hit, 16 units | 23.99 | 14.94 | 104 → approximately 0 |
| BMP hit, 80 units | 35.46 | 56.66 | 296 → approximately 0 |
| Astral-starting hit, 80 UTF-16 units | 36.53 | 55.67 | 296 → approximately 0 |
| Three eligible dictionaries, final dictionary hit | 25.70 | 18.83 | 104 → approximately 0 |
| 256 deliberately equal-hash keys, 16-unit hit | 73.06 | 694.41 | 56 → approximately 0 |

The 80-unit cases regress **1.5–1.6x** despite allocation elimination. Range hashing/comparison has real CPU cost; intrinsic implementation differences are a plausible contributor, not proven by assembly analysis. The 256-key collision family regresses **9.5x**: String-compatible equal hashes form one linear-probe cluster, while HashMap has different collision handling. Spreading the same hash cannot separate genuinely equal hashes. Correctness is maintained, but this is a reason not to adopt the simple table unchanged.

## Correctness and changes

Complete `gradlew test` passed for library and CLI: **165 cases, 164 passed, one skipped, zero failures/errors**. Six new experiment tests include:

- 80 byte-equivalent String comparisons: two whole corpora, all 20 configurations, punctuation on/off.
- Dictionary precedence, longest match, null/empty replacements and collision verification, including zero hashes.
- BMP/supplementary starters, 63/64/65/80/300-unit keys, long-only starters, an 80-supplementary-character key, and unusual isolated-surrogate sequences.
- Custom append/override and later Taiwan/punctuation rounds.
- Comparator TreeMap, overridden HashMap.get and identity-map fallback.

Temporary experimental sources added:

- `src/test/java/openccjava/{PhraseSliceIndex,ExperimentalSliceOpenCC,PhraseSliceCiBench,PhraseSliceExperimentTest}.java`
- `src/jmh/java/openccjava/{PhraseSliceBenchmark,PhrasePreparationBenchmark,PhraseLookupBenchmark,PhraseColdBenchmark}.java`
- `bench/profiling/generate-phrase-experiment.ps1`
- This folder's memory agent, JFR analyzer, report, raw JSON and text logs. JFR binaries are local and Git-ignored by the existing profiling ignore rule.

All paths above are relative to the openccjava subproject. Existing unrelated untracked files were preserved. No merge, commit, publishing, production replacement, README claims or CHANGELOG adoption entry was made.

## Decision and next experiment

**Refine and retest.** Preserve the production baseline. The large allocation savings and normal-corpus speedups justify further work, but the following need resolution before adoption:

1. Bound collision cost, for example a detected large collision group that falls back to the authoritative map, or a separately evaluated stronger secondary hash. Measure fallback allocation and adversarial worst cases; do not silently sacrifice correctness.
2. Investigate long-range hashing performance and whether retaining the original lookup for selected long-key cases provides a better tradeoff. Avoid adopting an arbitrary length threshold without measurements.
3. Evaluate a dictionary-scoped cache with clear reclamation/invalidation semantics and measure all-configuration retained memory, rather than converter-local rebuilding.
4. Repeat with interleaved/randomized variant order, longer runs and more forks, deployment JVMs, multiple concurrent callers and realistic varied corpora. Tiny-input CI/JMH divergence deserves a focused check.

None of these refinements was implemented in this experiment.

## Reproduction

Run commands sequentially from repository root in PowerShell:

```powershell
& ./openccjava/bench/profiling/generate-phrase-experiment.ps1
./gradlew.bat test :openccjava:jmhJar --console=plain
java -jar openccjava/build/libs/openccjava-1.5.1-jmh.jar PhraseSliceBenchmark -wi 3 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff openccjava/bench/profiling/phrase-slice/conversion.json
java -jar openccjava/build/libs/openccjava-1.5.1-jmh.jar PhrasePreparationBenchmark -wi 3 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff openccjava/bench/profiling/phrase-slice/preparation.json
java -jar openccjava/build/libs/openccjava-1.5.1-jmh.jar PhraseLookupBenchmark -wi 2 -i 3 -w 1s -r 1s -f 1 -t 1 -prof gc -rf json -rff openccjava/bench/profiling/phrase-slice/lookup.json
java -jar openccjava/build/libs/openccjava-1.5.1-jmh.jar PhraseColdBenchmark -rf json -rff openccjava/bench/profiling/phrase-slice/cold.json
java -jar openccjava/build/libs/openccjava-1.5.1-jmh.jar S2tCorpusBenchmark -wi 3 -i 5 -w 1s -r 1s -f 1 -t 1 -prof gc
java -cp 'openccjava/build/classes/java/main;openccjava/build/classes/java/test;openccjava/build/resources/main' openccjava.PhraseSliceCiBench baseline
java -cp 'openccjava/build/classes/java/main;openccjava/build/classes/java/test;openccjava/build/resources/main' openccjava.PhraseSliceCiBench slice
```

JFR uses one matcher per output file to avoid overwrite across forks. Set a writable `-XX:FlightRecorderOptions=repository=...`, then `-XX:StartFlightRecording=filename=...,settings=profile,dumponexit=true` and DebugNonSafepoints. Exact arguments are recorded in `baseline-jfr.log` / `slice-jfr.log`. Analyze with `ExperimentJfrSummary.java recording.jfr baseline` or `... slice`. The retained-memory utility is `PhraseIndexMemory.java`, compiled with main/test classes and launched as a javaagent; its measured output is `memory.txt`.

Raw results: `conversion.json`, `comparison.json`, `preparation.json`, `lookup.json`, `lookup-comparison.json`, `cold.json`, `existing-jmh.json`, CI logs, and both profile summaries.
