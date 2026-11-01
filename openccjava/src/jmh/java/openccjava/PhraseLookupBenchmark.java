package openccjava;
import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class PhraseLookupBenchmark {
    @Param({"baseline", "slice"}) public String matcher;
    @Param({"hit3", "miss16", "hit16", "hit80", "astral80", "collision", "multiple"}) public String scenario;
    private Map<String,String>[] maps;
    private PhraseSliceIndex[] indexes;
    private String input;
    private int length;
    @SuppressWarnings("unchecked")
    @Setup public void setup() {
        length = scenario.equals("hit3") ? 3 : scenario.contains("80") ? 80 : 16;
        StringBuilder key = new StringBuilder();
        if (scenario.equals("astral80")) key.appendCodePoint(0x20000);
        else key.append('中');
        while (key.length() < length) key.append('文');
        int count = scenario.equals("multiple") ? 3 : 1;
        maps = new Map[count]; indexes = new PhraseSliceIndex[count];
        for (int i = 0; i < count; i++) maps[i] = new HashMap<>();
        String candidate = key.toString();
        maps[count - 1].put(candidate, "hit");
        for (int i = 0; i < count; i++) maps[i].put("无" + candidate.substring(1), "other");
        if (scenario.equals("miss16")) candidate = "中" + candidate.substring(1, length - 1) + "字";
        if (scenario.equals("collision")) {
            length = 16;
            // 256 distinct equal-hash keys; same probe sequence, content must still be checked.
            for (int combination = 0; combination < 256; combination++) {
                StringBuilder colliding = new StringBuilder();
                for (int bit = 0; bit < 8; bit++) colliding.append((combination & (1 << bit)) == 0 ? "Aa" : "BB");
                maps[0].put(colliding.toString(), "collision");
            }
            candidate = "BBBBBBBBBBBBBBBB";
        }
        input = "!" + candidate + "!";
        for (int i = 0; i < count; i++) indexes[i] = new PhraseSliceIndex(maps[i]);
    }
    @Benchmark public String lookup() {
        if (matcher.equals("baseline")) {
            String key = input.substring(1, length + 1);
            for (int i = 0; i < maps.length; i++) { String value = maps[i].get(key); if (value != null) return value; }
        } else {
            int hash = PhraseSliceIndex.hashRange(input, 1, length);
            for (int i = 0; i < indexes.length; i++) { String value = indexes[i].lookup(input, 1, length, hash); if (value != null) return value; }
        }
        return null;
    }
}
