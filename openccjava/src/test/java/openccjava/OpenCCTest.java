package openccjava;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OpenCCTest {
    static OpenCC opencc;

    @BeforeAll
    static void init() {
        opencc = new OpenCC("s2t");
    }

    @Test
    void testConvertS2T() {
        String simplified = "简体中文测试";
        String expectedTraditional = "簡體中文測試"; // Ensure your dictionary has these mappings
        String result = opencc.convert(simplified);

        assertEquals(expectedTraditional, result);
        assertTrue(result.contains("簡"), "Should contain converted character");
    }

    @Test
    void testPunctuationConversionS2T() {
        String input = "“你好”";
        String result = opencc.s2t(input, true);
        assertEquals("「你好」", result);
    }

    @Test
    void testS2TwpCombinesTaiwanPhraseAndVariantNormalizationAfterS2T() {
        OpenCC cc = OpenCC.fromConfig(OpenccConfig.S2TWP);

        assertEquals("軟體為", cc.convert("软件为"));
        assertEquals("軟體眾", cc.convert("软件众"));
    }

    @Test
    void testZhoCheckTraditional() {
        String text = "繁體中文";
        int result = OpenCC.zhoCheck(text);
        assertEquals(1, result); // 1 = traditional
    }

    @Test
    void testZhoCheckSimplified() {
        String text = "简体中文";
        int result = OpenCC.zhoCheck(text);
        assertEquals(2, result); // 2 = simplified
    }

    @Test
    void testZhoCheckUnknown() {
        String text = "hello world!";
        int result = OpenCC.zhoCheck(text);
        assertEquals(0, result); // not Chinese
    }

    @Test
    void testConfigFallback() {
        OpenCC bad = OpenCC.fromConfig("invalid_config");
        assertEquals("s2t", bad.getConfig());
        assertTrue(bad.hasLastError());
        assertNotNull(bad.getLastError());
        bad.clearLastError();
        assertNull(bad.getLastError());
    }

    @Test
    void testConfigEnum() {
        OpenccConfig configEnum = OpenccConfig.tryParse("s2twp");
        String ConfigStr = configEnum.toCanonicalName();
        assertTrue(OpenCC.isSupportedConfig(ConfigStr));
        assertEquals("s2twp", ConfigStr);
    }

    @Test
    void testConfigEnumRoundTrip() {
        // ✅ Case-insensitive matching
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("s2twp"));
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("S2Twp"));
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("S2TWP"));
        assertEquals(OpenccConfig.S2HKP, OpenccConfig.tryParse("s2hkp"));
        assertEquals(OpenccConfig.HK2SP, OpenccConfig.tryParse("hk2sp"));
        // ✅ Round-trip consistency
        for (OpenccConfig cfg : OpenccConfig.values()) {
            assertEquals(cfg, OpenccConfig.tryParse(cfg.toCanonicalName()));
            assertEquals(cfg.toCanonicalName(), cfg.toCanonicalName().toLowerCase()); // ensure lowercase form
        }
    }

    @Test
    void testSupportedConfigsIncludeDirectHongKongPhraseConfigs() {
        assertTrue(OpenCC.getSupportedConfigs().contains("s2hkp"));
        assertTrue(OpenCC.getSupportedConfigs().contains("hk2sp"));
    }

    @Test
    void testInvalidConfigTryParseReturnsNull() {
        // ✅ Null input → tolerant: returns null
        assertNull(OpenccConfig.tryParse(null));

        // ✅ Empty / whitespace → tolerant: returns null
        assertNull(OpenccConfig.tryParse(""));
        assertNull(OpenccConfig.tryParse("   "));

        // ✅ Unknown config → tolerant: returns null
        assertNull(OpenccConfig.tryParse("invalid"));
        assertNull(OpenccConfig.tryParse("t2xyz"));
    }

    @Test
    void testInvalidConfigIsRejected() {
        // ✅ Null / empty / whitespace
        assertFalse(OpenccConfig.isValidConfig(null));
        assertFalse(OpenccConfig.isValidConfig(""));
        assertFalse(OpenccConfig.isValidConfig("   "));

        // ✅ Unknown config
        assertFalse(OpenccConfig.isValidConfig("invalid"));
        assertFalse(OpenccConfig.isValidConfig("t2xyz"));

        // ✅ tryParse returns null for invalid inputs
        assertNull(OpenccConfig.tryParse(null));
        assertNull(OpenccConfig.tryParse(""));
        assertNull(OpenccConfig.tryParse("invalid"));
        assertNull(OpenccConfig.tryParse("t2xyz"));
    }

    @Test
    void testTryParseRoundTrip() {
        for (OpenccConfig c : OpenccConfig.values()) {
            assertEquals(c, OpenccConfig.tryParse(c.toCanonicalName()));
            assertEquals(c, OpenccConfig.tryParse(c.name())); // enum-style
        }
    }

    @Test
    void testDirectHongKongPhraseConfigConversions() {
        DictionaryMaxlength dict = minimalDirectHongKongPhraseDictionary();

        OpenCC s2hkp = new OpenCC(OpenccConfig.S2HKP, dict);
        assertEquals("港字詞", s2hkp.convert("汉字词"));

        OpenCC hk2sp = new OpenCC(OpenccConfig.HK2SP, dict);
        assertEquals("汉字词", hk2sp.convert("港字詞"));
    }

    @Test
    void testDirectHongKongPhraseApis() {
        OpenCC cc = new OpenCC(OpenccConfig.S2T, minimalDirectHongKongPhraseDictionary());

        assertEquals("港字詞", cc.s2hkp("汉字词", false));
        assertEquals("汉字词", cc.hk2sp("港字詞", false));
    }

    @Test
    void testT2JPUsesShinjitaiCharactersRev() {
        OpenCC cc = OpenCC.fromConfig(OpenccConfig.T2JP);

        assertEquals("旧字体：広国，読売。", cc.t2jp("舊字體：廣國，讀賣。"));
    }

    @Test
    void testJP2TUsesShinjitaiPhrasesAndCharacters() {
        OpenCC cc = OpenCC.fromConfig(OpenccConfig.JP2T);

        assertEquals("舊字體：廣國，讀賣。", cc.jp2t("旧字体：広国，読売。"));
    }

    private static DictionaryMaxlength minimalDirectHongKongPhraseDictionary() {
        DictionaryMaxlength dict = new DictionaryMaxlength();
        Map<String, String> stCharacters = new HashMap<>();
        stCharacters.put("汉", "漢");
        stCharacters.put("词", "詞");
        dict.st_characters = entry(stCharacters);
        dict.st_phrases = new DictionaryMaxlength.DictEntry();
        dict.st_punctuations = new DictionaryMaxlength.DictEntry();
        Map<String, String> tsCharacters = new HashMap<>();
        tsCharacters.put("漢", "汉");
        tsCharacters.put("詞", "词");
        dict.ts_characters = entry(tsCharacters);
        dict.ts_phrases = new DictionaryMaxlength.DictEntry();
        dict.ts_punctuations = new DictionaryMaxlength.DictEntry();
        dict.tw_phrases = new DictionaryMaxlength.DictEntry();
        dict.tw_phrases_rev = new DictionaryMaxlength.DictEntry();
        dict.tw_variants = new DictionaryMaxlength.DictEntry();
        dict.tw_variants_phrases = new DictionaryMaxlength.DictEntry();
        dict.tw_variants_rev = new DictionaryMaxlength.DictEntry();
        dict.tw_variants_rev_phrases = new DictionaryMaxlength.DictEntry();
        dict.hk_phrases = entry(pair("漢字詞", "港字詞"));
        dict.hk_phrases_rev = entry(pair("港字詞", "漢字詞"));
        dict.hk_variants = new DictionaryMaxlength.DictEntry();
        dict.hk_variants_phrases = new DictionaryMaxlength.DictEntry();
        dict.hk_variants_rev = new DictionaryMaxlength.DictEntry();
        dict.hk_variants_rev_phrases = new DictionaryMaxlength.DictEntry();
        dict.jps_characters = new DictionaryMaxlength.DictEntry();
        dict.jps_characters_rev = new DictionaryMaxlength.DictEntry();
        dict.jps_phrases = new DictionaryMaxlength.DictEntry();
        return dict;
    }

    private static DictionaryMaxlength.DictEntry entry(Map<String, String> pairs) {
        int maxLength = 0;
        int minLength = Integer.MAX_VALUE;
        for (String key : pairs.keySet()) {
            maxLength = Math.max(maxLength, key.length());
            minLength = Math.min(minLength, key.length());
        }
        if (pairs.isEmpty()) {
            minLength = 0;
        }
        return new DictionaryMaxlength.DictEntry(pairs, maxLength, minLength);
    }

    private static Map<String, String> pair(String key, String value) {
        Map<String, String> pairs = new HashMap<>();
        pairs.put(key, value);
        return pairs;
    }

    @Test
    void testSetVerboseLogging() {
        // Ensure initial state known
        OpenCC.setVerboseLogging(false);
        assertEquals(Level.OFF, OpenCC.LOGGER.getLevel(), "Logger should be OFF when disabled");
        // Enable verbose logging
        OpenCC.setVerboseLogging(true);
        assertEquals(Level.INFO, OpenCC.LOGGER.getLevel(), "Logger should be INFO when enabled");
        // Disable again
        OpenCC.setVerboseLogging(false);
        assertEquals(Level.OFF, OpenCC.LOGGER.getLevel(), "Logger should be OFF when disabled again");
    }

    @Test
    public void testSegmentReplace_s2t_100kChars() {
        // Sample repeated simplified Chinese text (e.g., "汉字转换")
        String base = "“数大”便是美，碧绿的山坡前几千只绵羊，挨成一片的雪绒，是美；";
        // Java 8 replacement for String.repeat()
        StringBuilder sb = new StringBuilder(100_000);
        while (sb.length() < 100_000) {
            sb.append(base);
        }
        String input = sb.substring(0, 100_000); // trim to exactly 100k chars
        // Setup OpenCC
        DictionaryMaxlength d = DictionaryMaxlength.fromDicts(); // assume your preloaded dictionary method
        OpenCC cc = new OpenCC(); // your wrapper class

        // Time the conversion
        long start = System.nanoTime();
        String output = cc.segmentReplace(
                input,
                Arrays.asList(d.st_phrases, d.st_characters), // Java 8 replacement for List.of(...)
                16
        );
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        // Assertions and output
        assertNotNull(output);
        assertEquals(input.length(), output.length()); // Simplified-to-Traditional is 1:1 in this test
        System.out.println("Converted 100K chars in " + durationMs + " ms");
    }

    @Test
    public void testS2T_100kCharacters() {
        // Generate 100,000 characters from a repeated simplified phrase
        String base = "“数大”便是美，碧绿的山坡前几千只绵羊，挨成一片的雪绒，是美；";
        StringBuilder inputBuilder = new StringBuilder(100_000);
        while (inputBuilder.length() < 100_000) {
            inputBuilder.append(base);
        }
        String input = inputBuilder.toString();
        // Create OpenCC instance (your optimized one)
        OpenCC opencc = OpenCC.fromConfig(OpenccConfig.S2T); // assuming your constructor loads config
        // Time the conversion
        long start = System.nanoTime();
        String output = opencc.s2t(input, false); // simplified to traditional
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        // Assertions
        assertEquals(OpenccConfig.values().length, OpenCC.getSupportedConfigIds().size());
        assertEquals(OpenccConfig.S2T, opencc.getConfigId());
        assertNotNull(output);
        assertEquals(input.length(), output.length()); // rough check, assuming 1:1 mapping
        System.out.println("s2t() conversion of 100K chars completed in " + durationMs + " ms");
    }

    @Test
    void testOpenCCFromDictsAppendCustomFile() throws Exception {
        Path custom = Files.createTempFile(
                "openccjava-opencc-append-",
                ".txt"
        );

        Files.write(
                custom,
                Collections.singletonList("软件\t軟體"),
                StandardCharsets.UTF_8
        );

        OpenCC cc = OpenCC.fromDicts(
                OpenccConfig.S2T,
                Collections.singletonList(
                        CustomDictSpec.fromFile(
                                DictSlot.STPhrases,
                                custom,
                                CustomDictMode.Append
                        )
                )
        );

        String result = cc.convert("软件");

        assertEquals("軟體", result);

        Files.deleteIfExists(custom);
    }

    @Test
    void testOpenCCFromDictsOverrideCustomFile() throws Exception {
        Path custom = Files.createTempFile(
                "openccjava-opencc-override-",
                ".txt"
        );

        try {
            Files.write(
                    custom,
                    Arrays.asList(
                            "测试词\t專用詞",
                            "帕兰蒂尼\t勃蘭蒂尼"
                    ),
                    StandardCharsets.UTF_8
            );

            OpenCC cc = OpenCC.fromDicts(
                    OpenccConfig.S2T,
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Override
                            )
                    )
            );

            assertEquals("專用詞", cc.convert("测试词"));
            assertEquals("勃蘭蒂尼", cc.convert("帕兰蒂尼"));
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    @Test
    void testOpenCCFromDictsBaseOverrideCustomFile() throws Exception {
        Path custom = Files.createTempFile(
                "openccjava-opencc-override-",
                ".txt"
        );

        try {
            Files.write(
                    custom,
                    Arrays.asList(
                            "测试词\t專用詞",
                            "帕兰蒂尼\t勃蘭蒂尼"
                    ),
                    StandardCharsets.UTF_8
            );

            OpenCC cc = OpenCC.fromDicts(
                    OpenccConfig.S2T,
                    "dicts",
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Override
                            )
                    )
            );

            assertEquals("專用詞", cc.convert("测试词"));
            assertEquals("勃蘭蒂尼", cc.convert("帕兰蒂尼"));
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    // DeTofu Tests

    @Test
    void testDeTofuBuiltin() {
        OpenCC cc = new OpenCC();

        String output = cc.deTofu(
                "骖𬴂",
                DeTofu.Level.ExtB
        );

        assertEquals("骖騑", output);
    }

    @Test
    void testDeTofuPreservesUnmappedCharacter() {
        OpenCC cc = new OpenCC();

        String output = cc.deTofu(
                "𱁬",
                DeTofu.Level.ExtB
        );

        assertEquals("𱁬", output);
    }

    @Test
    void testOpenCCT2SDeTofu() {
        OpenCC cc = new OpenCC(OpenccConfig.T2S);

        String output = cc.deTofu(
                cc.convert("儼驂騑於上路，訪風景於崇阿"),
                DeTofu.Level.ExtB
        );

        assertEquals(
                "俨骖騑于上路，访风景于崇阿",
                output
        );
    }

    @Test
    void testDeTofuWithCustomFileOverridesBuiltin() throws IOException {
        Path path = Files.createTempFile(
                "openccjava-detofu-",
                ".txt"
        );

        try {
            Files.write(
                    path,
                    "𣭲\t氂\tB\n".getBytes(StandardCharsets.UTF_8)
            );

            OpenCC cc = new OpenCC();

            String output = cc.deTofuWithCustomFile(
                    "𣭲毛",
                    DeTofu.Level.ExtB,
                    path.toString()
            );

            assertEquals("氂毛", output);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void testOpenCCT2SDeTofuPreservesUnmappedCharacter() {
        OpenCC cc = new OpenCC(OpenccConfig.T2S);

        String output = cc.deTofu(
                cc.convert("儼驂騑於上路，訪風景於崇阿，𱁬"),
                DeTofu.Level.ExtB
        );

        assertEquals(
                "俨骖騑于上路，访风景于崇阿，𱁬",
                output
        );
    }

    @Test
    void testDeTofuWithCustomPairsOverridesBuiltin() {
        Map<String, String> pairs = new HashMap<>();

        pairs.put("𣭲", "氂");

        OpenCC cc = new OpenCC();

        String output = cc.deTofuWithCustomPairs(
                "𣭲毛",
                DeTofu.Level.ExtB,
                pairs
        );

        assertEquals("氂毛", output);
    }
}
