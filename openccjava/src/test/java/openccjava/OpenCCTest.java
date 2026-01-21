package openccjava;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

class OpenCCTest {
    static OpenCC opencc;

    @BeforeAll
    static void init()
    {
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
        assertNotNull(bad.getLastError());
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
        assertEquals(OpenccConfig.S2Twp, OpenccConfig.tryParse("s2twp"));
        assertEquals(OpenccConfig.S2Twp, OpenccConfig.tryParse("S2Twp"));
        assertEquals(OpenccConfig.S2Twp, OpenccConfig.tryParse("S2TWP"));
        // ✅ Round-trip consistency
        for (OpenccConfig cfg : OpenccConfig.values()) {
            assertEquals(cfg, OpenccConfig.tryParse(cfg.toCanonicalName()));
            assertEquals(cfg.toCanonicalName(), cfg.toCanonicalName().toLowerCase()); // ensure lowercase form
        }
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
        OpenCC opencc = new OpenCC("s2t"); // assuming your constructor loads config
        // Time the conversion
        long start = System.nanoTime();
        String output = opencc.s2t(input, false); // simplified to traditional
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        // Assertions
        assertNotNull(output);
        assertEquals(input.length(), output.length()); // rough check, assuming 1:1 mapping
        System.out.println("s2t() conversion of 100K chars completed in " + durationMs + " ms");
    }
}
