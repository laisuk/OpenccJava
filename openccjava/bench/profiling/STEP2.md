# Step 2: precomputed single-code-point lookup

Implemented and measured on 2026-10-05. `SingleCodePointIndex` is prepared when a `DictRefs.DictPartition` is constructed, and reused through the existing conversion-plan cache.

BMP code points use a direct `String[]` lookup. Supplementary code points use sorted `int[]` keys and `String[]` values with binary search, avoiding boxed lookup keys. Replacement Strings are referenced from dictionaries, not copied. No public API or dependency was added; Java 8 compatibility is retained.

Phrase matching remains unchanged and runs first. The index preserves the first non-null hit in dictionary order, supports empty replacements, handles valid surrogate pairs and isolated surrogate keys, and excludes two-BMP-character keys from the single-code-point fallback. As with existing cached metadata, dictionaries must remain immutable after configuration.

## Conversion results

Fresh before/after runs on Oracle HotSpot JDK 21.0.12.1, Windows, Intel i5-13400 / 16 logical processors. Baseline includes step 1. Both inputs are whole files; the long input has 1,130,220 UTF-16 units. One JMH caller, default internal parallelism, two fresh JVM forks, three 1-second warmups and five 1-second measurements per fork, GC profiler. Input reading and initial preparation occur outside conversion timing.

| Whole corpus | Before ms/op | After ms/op | Throughput increase | Allocated bytes before → after |
| --- | ---: | ---: | ---: | ---: |
| Short, 7,340 units | 0.1975 ± 0.0112 | 0.1755 ± 0.0123 | 12.5% | 1,743,872 → 1,414,112 |
| Long, 1,130,220 units | 29.6267 ± 1.5045 | 24.8039 ± 0.4409 | 19.4% | 288,706,440 → 237,193,864 |

Throughput increase is `beforeTime / afterTime - 1`; latency reduction is about 11.1% short and 16.3% long. Allocated bytes/op decrease by 18.9% short and 17.8% long. Errors are JMH's reported 99.9% errors. Short timing intervals narrowly overlap, so its exact gain is less certain; long timing separates clearly in this local run. These are exploratory host-specific measurements, not guarantees across workloads/JVMs.

## Dynamic preparation cost

The new `ConversionPlanBenchmark` separates partition construction (existing starter union already prepared), full fresh plan construction (starter union rebuilt too), and cache lookup. The dictionary is loaded in trial setup. Thus these are **preparation costs using an already loaded dictionary**, not a whole-JVM cold-start or dictionary-loading measurement.

| Preparation operation | Before µs/op | After µs/op | Allocated bytes before → after |
| --- | ---: | ---: | ---: |
| Partition with existing union | 0.0311 ± 0.0004 | 79.4027 ± 2.4920 | 272 → 335,721 |
| Fresh S2T plan including union | 504.4888 ± 46.5935 | 597.6530 ± 36.8063 | 1,444,565 → 1,779,991 |
| Cached S2T plan lookup | 0.00510 ± 0.00008 | 0.00527 ± 0.00022 | 48 → 48 |

Full plan preparation increases by approximately **93 µs / 18.5%**, with about **335 KB extra transient-plus-retained allocation**. The cached lookup is effectively unchanged. The full-plan difference is noisier than the isolated partition measurement and should not be treated as an exact cold-start penalty.

Using the full-plan and conversion point estimates gives an amortization estimate: 93.16 µs / 22.01 µs saved per short conversion ≈ 4.2 calls, or about **five short conversions**. A single long conversion saves approximately 4.82 ms, exceeding that estimated preparation overhead. This is an arithmetic steady-state estimate; it does not include cold JIT compilation, dictionary IO, or first-invocation scheduling effects. For tiny one-off calls or memory-sensitive deployments, preparation and retention still matter.

## Retained index memory

Measured separately with `Instrumentation.getObjectSize`, summing the index object and its owned arrays, excluding dictionary-owned replacement Strings and existing partition/union objects:

| Plan | Added retained index bytes | Approximate KiB |
| --- | ---: | ---: |
| S2T, punctuation false | 271,352 | 265.0 |
| S2T, punctuation true | 271,352 | 265.0 |
| S2TWP, punctuation false | 533,568 | 521.1 |
| S2TWP, punctuation true | 533,568 | 521.1 |

These figures depend on JVM reference width and object alignment. Each prepared partition owns its index; different configurations/punctuation plans can retain separate indexes even when they share dictionaries or starter unions. Reusing an already cached plan does not rebuild its index. An index with no BMP mappings avoids allocating the BMP table. The measured retained footprint is distinct from build allocation, which also includes temporary supplementary-tree nodes and wrappers.

## Correctness

- Saved original outputs before the change, then compared outputs byte-for-byte for both whole corpora across all 20 configurations, with punctuation disabled and enabled: **80 comparisons passed**.
- Added six focused regressions covering first-hit precedence, null values, empty replacements, supplementary range endpoints, unmatched code points, two-BMP-character keys, phrase priority, isolated surrogates and valid surrogate pairs.
- Library tests: **133 cases, 132 passed, 1 skipped, zero failures/errors**.
- Java 8-targeted library and JMH build succeed; production diff whitespace checks pass.
- Existing user files and the modified library JAR were preserved; nothing was committed or published.

## Evidence and reproduction

Raw files: `step2-before.json`, `step2-after.json`, corresponding logs, `step2-comparison.json`, `step2-memory.txt`, and `step2-golden-{save,check}.log`. Golden output files and the corpus comparison utility remain under ignored `openccjava/build`.

From repository root, PowerShell:

```powershell
./gradlew.bat :openccjava:test :openccjava:jmhJar --console=plain
java -jar openccjava/build/libs/openccjava-1.5.0-jmh.jar '(S2tCorpusBenchmark|ConversionPlanBenchmark)' -wi 3 -i 5 -w 1s -r 1s -f 2 -t 1 -prof gc -rf json -rff openccjava/bench/profiling/step2-repeat.json
```

The retained-memory utility is outside production sources:

```powershell
New-Item -ItemType Directory -Force openccjava/build/index-memory | Out-Null
Set-Content openccjava/build/index-memory/manifest.mf 'Premain-Class: IndexMemory'
javac -cp 'openccjava/build/classes/java/main;openccjava/build/resources/main' -d openccjava/build/index-memory openccjava/bench/profiling/IndexMemory.java
& 'C:/Program Files/Java/jdk-21.0.12.1/bin/jar.exe' --create --file openccjava/build/index-memory/agent.jar --manifest openccjava/build/index-memory/manifest.mf -C openccjava/build/index-memory IndexMemory.class
java '-javaagent:openccjava/build/index-memory/agent.jar' -cp 'openccjava/build/index-memory;openccjava/build/classes/java/main;openccjava/build/resources/main' IndexMemory
```

## Assessment

Keep this candidate: it produces a measurable long-text gain and lower allocation, with a small one-time preparation cost for reused converters. Follow-up validation should include real tiny inputs, memory-sensitive deployments, concurrent callers and relevant supported JVM versions. Index sharing across equivalent cached partitions or a sparse/page-based BMP table could reduce retained memory, but should be evaluated separately rather than bundled into this change. Phrase matching (step 3) remains untouched.
