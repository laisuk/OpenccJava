package openccjava;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PhraseSliceExperimentTest {
    private static String repeat(String unit, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(unit);
        return sb.toString();
    }
    private static DictionaryMaxlength.DictEntry entry(Map<String, String> map) {
        int min = Integer.MAX_VALUE, max = 0;
        for (String key : map.keySet()) { min = Math.min(min, key.length()); max = Math.max(max, key.length()); }
        return new DictionaryMaxlength.DictEntry(map, max, min);
    }
    private static void compare(String text, List<DictionaryMaxlength.DictEntry> dicts) {
        DictRefs plan = new DictRefs(dicts, StarterUnion.build(dicts));
        assertEquals(plan.applySegmentReplace(text, new OpenCC("s2t")::segmentReplaceWithUnion),
                plan.applySegmentReplace(text, new ExperimentalSliceOpenCC()::segmentReplaceWithUnion));
    }
    @Test void allConfigurationsBothCorporaAndPunctuation() throws Exception {
        for (String file : new String[]{"sample.txt", "SanWenHansSeal.txt"}) {
            Path path = Paths.get("bench", file);
            if (!Files.exists(path)) path = Paths.get("openccjava/bench", file);
            String input = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            OpenCC baseline = new OpenCC("s2t"), experiment = new ExperimentalSliceOpenCC();
            for (OpenccConfig config : OpenccConfig.values()) {
                baseline.setConfig(config); experiment.setConfig(config);
                for (boolean punct : new boolean[]{false, true}) assertEquals(baseline.convert(input, punct), experiment.convert(input, punct), config + " " + file);
            }
        }
    }
    @Test void collisionsVerifyContentAndZeroHashesWork() {
        Map<String, String> map = new HashMap<>();
        map.put("AaAa", "one"); map.put("BBBB", "two"); map.put("\u0000\u0000\u0000", "zero"); map.put("empty", "");
        assertEquals("AaAa".hashCode(), "BBBB".hashCode());
        PhraseSliceIndex index = new PhraseSliceIndex(map);
        for (String key : map.keySet()) {
            String input = "prefix" + key + "suffix";
            assertEquals(key.hashCode(), PhraseSliceIndex.hashRange(input, 6, key.length()));
            assertEquals(map.get(key), index.lookup(input, 6, key.length(), key.hashCode()));
        }
        assertNull(index.lookup("AaBB", 0, 4, "AaBB".hashCode()));
        compare("AaAa BBBB empty", Collections.singletonList(entry(map)));
    }
    @Test void precedenceLongestAndNullValues() {
        Map<String, String> first = new HashMap<>(), second = new HashMap<>();
        first.put("汉字词", "first"); first.put("汉字", "short"); first.put("空值词", null);
        second.put("汉字词", "second"); second.put("汉字词组", "longest"); second.put("空值词", "fallback");
        compare("汉字词组 汉字词 空值词", Arrays.asList(entry(first), entry(second)));
        compare("汉字词组 汉字词 空值词", Arrays.asList(entry(second), entry(first)));
    }
    @Test void longSupplementaryAndUnusualSurrogates() {
        Map<String, String> map = new HashMap<>();
        for (int len : new int[]{63,64,65,80,300}) {
            map.put("X" + repeat("文", len-1), "bmp" + len);
            map.put("\uD840\uDC00" + repeat("文", len-2), "astral" + len);
        }
        map.put(repeat("\uD840\uDC00", 80), "seal shaped");
        map.put("\uD840x\uDC00", "isolated"); map.put("\uD840", "half");
        for (String key : map.keySet()) compare(key + " " + key, Collections.singletonList(entry(map)));
    }
    @Test void unsupportedMapSemanticsUseOriginalLookup() {
        Map<String, String> tree = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        tree.put("XAb", "case-insensitive");
        assertFalse(PhraseSliceIndex.supports(tree));
        compare("Xab XAb", Collections.singletonList(entry(tree)));
        Map<String, String> custom = new HashMap<String, String>() {
            @Override public String get(Object key) { return super.get(key) == null ? "computed" : super.get(key); }
        };
        custom.put("XAb", "exact");
        compare("Xzz XAb", Collections.singletonList(entry(custom)));
        Map<String,String> identity = new IdentityHashMap<>(); identity.put("XAb", "identity");
        compare("XAb XAb", Collections.singletonList(entry(identity)));
    }
    @Test void customAppendOverrideAndRounds() {
        String source = "X" + repeat("文", 79), intermediate = "Y" + repeat("字", 64);
        for (CustomDictMode mode : CustomDictMode.values()) {
            List<CustomDictSpec> specs = Arrays.asList(
                    CustomDictSpec.fromPairs(DictSlot.STPhrases, Collections.singletonMap(source, intermediate), mode),
                    CustomDictSpec.fromPairs(DictSlot.TWVariantsPhrases, Collections.singletonMap(intermediate, "mapped"), mode));
            OpenCC baseline = new OpenCC(OpenccConfig.S2TWP, specs);
            OpenCC experiment = new ExperimentalSliceOpenCC(OpenccConfig.S2TWP, specs);
            for (boolean punct : new boolean[]{false, true}) assertEquals(baseline.convert("“" + source + "”", punct), experiment.convert("“" + source + "”", punct));
        }
    }
}
