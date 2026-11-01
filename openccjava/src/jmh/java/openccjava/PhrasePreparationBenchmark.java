package openccjava;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class PhrasePreparationBenchmark {
    @Param({"baseline", "slice"}) public String matcher;
    private DictionaryMaxlength dictionary;
    private DictRefs plan;
    @Setup public void setup() {
        dictionary = OpenCC.DictionaryHolder.get();
        plan = new ConversionPlanCache(() -> dictionary).getPlan(OpenccConfig.S2T, false);
    }
    @Benchmark public Object indexOnly() {
        return matcher.equals("slice") ? new PhraseSliceIndex(dictionary.st_phrases.dict) : dictionary.st_phrases.dict;
    }
    @Benchmark public Object freshPlan() {
        DictRefs fresh = new ConversionPlanCache(() -> dictionary).getPlan(OpenccConfig.S2T, false);
        OpenCC cc = matcher.equals("slice") ? new ExperimentalSliceOpenCC() : new OpenCC("s2t");
        if (cc instanceof ExperimentalSliceOpenCC) ((ExperimentalSliceOpenCC) cc).prepare(fresh);
        return new Prepared(cc, fresh);
    }
    @Benchmark public String firstConversionPreparedDictionary() {
        if (matcher.equals("baseline")) return plan.applySegmentReplace("汉字转换测试", new OpenCC("s2t")::segmentReplaceWithUnion);
        return plan.applySegmentReplace("汉字转换测试", new ExperimentalSliceOpenCC()::segmentReplaceWithUnion);
    }
    private static final class Prepared {
        final OpenCC converter;
        final DictRefs plan;
        Prepared(OpenCC converter, DictRefs plan) { this.converter = converter; this.plan = plan; }
    }
}
