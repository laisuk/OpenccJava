package openccjava;
import java.lang.instrument.Instrumentation;
public class PhraseIndexMemory {
    private static Instrumentation instrumentation;
    public static void premain(String args, Instrumentation value) { instrumentation = value; }
    public static void main(String[] args) {
        ExperimentalSliceOpenCC cc = new ExperimentalSliceOpenCC();
        cc.prepare(new ConversionPlanCache(() -> OpenCC.DictionaryHolder.get()).getPlan(OpenccConfig.S2T, false));
        long bytes = 0;
        for (PhraseSliceIndex index : cc.dictionaryIndexes.values()) {
            bytes += instrumentation.getObjectSize(index) + instrumentation.getObjectSize(index.keys)
                    + instrumentation.getObjectSize(index.values) + instrumentation.getObjectSize(index.hashes);
            System.out.println("entries=" + OpenCC.DictionaryHolder.get().st_phrases.dict.size()
                    + " capacity=" + index.keys.length);
        }
        System.out.println("indexOwnedBytes=" + bytes + " estimated=" + cc.estimatedIndexBytes());
    }
}
