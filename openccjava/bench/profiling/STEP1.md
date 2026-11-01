# Step 1: indexed dictionary iteration

Implemented on 2026-10-05. Only the two dictionary lookup loops in `OpenCC.convertSegmentWithUnion` changed: iterate by index over the existing immutable ArrayList-backed partitions, caching dictionary counts once per segment. This preserves dictionary precedence, longest-phrase-first search, bounds filtering, character fallback and all existing first-hit exits. There are no new indexes, dependencies or public API changes.

The corpus benchmark now defaults to whole files (`maxChars=2147483647`). The optional cap remains available for size sweeps. The long corpus is **1,130,220 UTF-16 units** and 199,940 segments; the short corpus is **7,340 units**, 7,331 code points and 1,150 segments.

## Fresh before/after comparison

Same JDK 21.0.12.1 / i5-13400 host as the initial report. Both runs: direct `s2t(input, false)`, default internal parallelism, one JMH caller, two fresh JVM forks, three 1-second warmups and five 1-second measurements per fork, GC profiler. No conversion file IO or first plan preparation inside the timed operation.

| Whole corpus | Before ms/op | After ms/op | Before allocated bytes/op | After allocated bytes/op |
| --- | ---: | ---: | ---: | ---: |
| SanWenHansSeal.txt | 0.197 ± 0.009 | 0.196 ± 0.010 | 1,743,872 | 1,743,872 |
| sample.txt | 28.441 ± 1.160 | 28.645 ± 1.161 | 288,706,432 | 288,706,434 |

Error is JMH's reported 99.9% error interval, rounded. **There is no measurable normal-run latency or allocation improvement in these results.** Short latency differs by less than 1%, and long latency increases by about 0.7%; both changes are within measurement uncertainty. These results do not establish a regression or a speedup.

Raw data: `step1-before.json`, `step1-after.json`, and their logs. Do not compare the long result to the initial report's 999,999-unit prefix as though the workload were identical.

## Why retain the change?

The source and compiled hot path no longer request dictionary iterators. A follow-up short-corpus JFR recording no longer lists `ArrayList$Itr` at either dictionary loop among its top allocation sites. Substring String/backing-array sites now represent about 82% of that recording's conversion allocation weight.

However, the original sampled iterator weights did not translate into a reduction in the normal GC-profiler measurements. This limits the earlier profile's value for predicting an improvement: weighted allocation samples, warmup and instrumented JVM compilation behavior are not interchangeable with steady-state uninstrumented bytes/op. JIT iterator elimination in normal runs is a possible explanation, not a proven diagnosis. The small indexed-loop change removes explicit iterator creation while preserving behavior; it should not be advertised as a throughput optimization with a measured benefit on this JVM.

Runtime class-load logging confirmed that `OpenCC` came from the rebuilt JMH JAR; `javap` confirmed indexed `List.get` calls in the compiled conversion method. Stale library loading was ruled out.

## Correctness and checks

- Saved pre-change outputs for both whole corpora with punctuation disabled and enabled, then compared all four post-change outputs byte-for-byte: identical.
- Library suite: 127 cases, 126 passed, 1 skipped, zero failures/errors.
- JMH build succeeds; production code remains Java 8 compatible.
- `git diff --check` passes for the production edit.
- Existing user changes were preserved. No publishing, commits or replacement of the user-modified library JAR was performed.

The corpus comparison utility and golden files are under ignored `openccjava/build`; recordings are local and ignored. Post-change recording: `step1-short.jfr`; summary: `step1-short-summary.txt`. Its recording length differs from the initial short recording, so absolute allocation-weight totals should not be compared.

## Next candidate

Step 2 remains a separate change: direct single-code-point lookup, with careful supplementary-character handling and dictionary precedence. The reported Python result is encouraging but does not predict this JVM's gain. Measure its effect against this full-corpus baseline, and include the lookup table's construction and retained-memory costs.

## Reproduce the updated benchmark

From repository root in PowerShell:

```powershell
./gradlew.bat :openccjava:test :openccjava:jmhJar --console=plain
java -jar openccjava/build/libs/openccjava-1.5.0-jmh.jar S2tCorpusBenchmark -wi 3 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff openccjava/bench/profiling/step1-repeat.json
```

For a final release decision, use longer iterations and repeat on supported deployment JVMs; this investigation is an exploratory local comparison.
