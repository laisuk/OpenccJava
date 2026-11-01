import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import openccjava.*;

/** Measures index-owned retained objects; dictionary-owned Strings are excluded. */
public class IndexMemory {
    private static Instrumentation instrumentation;
    public static void premain(String args, Instrumentation value) { instrumentation = value; }

    public static void main(String[] args) throws Exception {
        for (OpenccConfig config : new OpenccConfig[]{OpenccConfig.S2T, OpenccConfig.S2TWP}) {
            for (boolean punctuation : new boolean[]{false, true}) {
                DictRefs plan = ConversionPlanCache.forDictionary(OpenCC.DictionaryHolder.get())
                        .getPlan(config, punctuation);
                Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
                long[] bytes = {0};
                int[] rounds = {0};
                plan.applySegmentReplace("", (input, partition) -> {
                    try {
                        Field field = partition.getClass().getDeclaredField("singleIndex");
                        field.setAccessible(true);
                        Object index = field.get(partition);
                        rounds[0]++;
                        if (index != null && seen.add(index)) {
                            bytes[0] += instrumentation.getObjectSize(index);
                            for (Field owned : index.getClass().getDeclaredFields()) {
                                owned.setAccessible(true);
                                Object array = owned.get(index);
                                if (array != null && seen.add(array)) bytes[0] += instrumentation.getObjectSize(array);
                            }
                        }
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                    return input;
                });
                System.out.println(config + " punctuation=" + punctuation + " rounds=" + rounds[0]
                        + " indexOwnedBytes=" + bytes[0]);
            }
        }
    }
}
