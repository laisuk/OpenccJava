package openccjava;

import org.openjdk.jmh.annotations.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Measures preparation separately from dictionary loading and conversion. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class ConversionPlanBenchmark {
    private DictionaryMaxlength dictionary;
    private List<DictionaryMaxlength.DictEntry> dictionaries;
    private StarterUnion union;
    private ConversionPlanCache cached;

    @Setup(Level.Trial)
    public void setup() {
        dictionary = OpenCC.DictionaryHolder.get();
        dictionaries = Arrays.asList(dictionary.st_phrases, dictionary.st_characters);
        union = StarterUnion.build(dictionaries);
        cached = new ConversionPlanCache(() -> dictionary);
        cached.getPlan(OpenccConfig.S2T, false);
    }

    @Benchmark
    public DictRefs preparePartition() {
        return new DictRefs(dictionaries, union);
    }

    @Benchmark
    public DictRefs preparePlanWithUnion() {
        return new ConversionPlanCache(() -> dictionary).getPlan(OpenccConfig.S2T, false);
    }

    @Benchmark
    public DictRefs cachedPlan() {
        return cached.getPlan(OpenccConfig.S2T, false);
    }
}
