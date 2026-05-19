package openccjava;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

//import java.io.File;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryLibTest {
    static final String TEST_JSON_OUTPUT = "test_dictionary_maxlength.json";
    static DictionaryMaxlength original;

    @BeforeAll
    static void setup() {
        original = DictionaryMaxlength.fromDicts();
    }

    @Test
    void testFromDictsLoadsAllFields() {
        assertNotNull(original);
        assertFalse(original.st_characters.dict.isEmpty(), "st_characters should be non-empty");
        assertFalse(original.ts_phrases.dict.isEmpty(), "ts_phrases should be non-empty");
        assertTrue(original.tw_variants_rev.maxLength > 0, "tw_variants_rev should have maxLength > 0");

    }

    @Test
    void testSerializeAndDeserializeJson() throws IOException {
        // Save to test file
        original.serializeToJsonNoDeps(TEST_JSON_OUTPUT);
        // Load again
        DictionaryMaxlength loaded = DictionaryMaxlength.fromJsonFileNoDeps(TEST_JSON_OUTPUT);
        // Validate one field at least
        assertEquals(
                original.st_characters.dict.get("你"),
                loaded.st_characters.dict.get("你"),
                "Key '你' translation must match"
        );

        assertEquals(
                original.st_characters.maxLength,
                loaded.st_characters.maxLength,
                "Max length should be preserved"
        );

        File file = new File(TEST_JSON_OUTPUT);
        file.deleteOnExit();
    }

    @Test
    void testToStringDisplaysCount() {
        String summary = original.toString();
        assertTrue(summary.contains("loaded dicts"), "toString should summarize dict count");
    }

    @Test
    void cleanupTestFile() throws IOException {
        Files.deleteIfExists(Paths.get(TEST_JSON_OUTPUT));
    }

    // Use this to generate JSON file when modify DictionaryMaxlength structure
    @Test
    @Disabled
    void testSerializeToJsonFromDicts() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts();
        // Where to output
        String outputPath = "dictionary_maxlength.json";
        // Serialize
        assertDoesNotThrow(() -> dict.serializeToJsonNoDeps(outputPath));
        // Confirm file is written
        File file = new File(outputPath);
        assertTrue(file.exists(), "Serialized JSON file should exist");
        assertTrue(file.length() > 0, "Serialized file should not be empty");

        System.out.println("✅ dictionary_maxlength.json written at: " + file.getAbsolutePath());
    }

    @Test
    void testFromDictsWithCustomAppendFile() throws IOException {
        Path custom = Files.createTempFile("openccjava-custom-stphrases-", ".txt");
        Files.write(
                custom,
                Collections.singletonList("软件\t軟體"),
                StandardCharsets.UTF_8
        );

        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromFile(
                                DictSlot.STPhrases,
                                custom,
                                CustomDictMode.Append
                        )
                )
        );

        assertEquals("軟體", dict.st_phrases.dict.get("软件"));
        assertTrue(dict.st_phrases.maxLength >= "软件".length());
        assertTrue(dict.st_phrases.minLength <= "软件".length());

        Files.deleteIfExists(custom);
    }

    @Test
    void testFromDictsWithCustomOverrideFile() throws IOException {
        Path custom = Files.createTempFile("openccjava-custom-stphrases-override-", ".txt");
        Files.write(
                custom,
                Collections.singletonList("帕兰蒂尔\t柏蘭蒂爾"),
                StandardCharsets.UTF_8
        );

        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                Collections.singletonList(
                        CustomDictSpec.fromFile(
                                DictSlot.STPhrases,
                                custom,
                                CustomDictMode.Override
                        )
                )
        );

        assertEquals("柏蘭蒂爾", dict.st_phrases.dict.get("帕兰蒂尔"));
        assertEquals(1, dict.st_phrases.dict.size());
        assertEquals("帕兰蒂尔".length(), dict.st_phrases.maxLength);
        assertEquals("帕兰蒂尔".length(), dict.st_phrases.minLength);

        Files.deleteIfExists(custom);
    }

    @Test
    void testFromDictsWithCustomOverrideFileTwoEntries() throws IOException {
        Path custom = Files.createTempFile("openccjava-custom-stphrases-override-", ".txt");

        try {
            Files.write(
                    custom,
                    Arrays.asList(
                            "测试词\t專用詞",
                            "测试鼠标\t測試滑鼠"
                    ),
                    StandardCharsets.UTF_8
            );

            DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Override
                            )
                    )
            );

            assertEquals(2, dict.st_phrases.dict.size());
            assertEquals("專用詞", dict.st_phrases.dict.get("测试词"));
            assertEquals("測試滑鼠", dict.st_phrases.dict.get("测试鼠标"));

            assertEquals("测试鼠标".length(), dict.st_phrases.maxLength);
            assertEquals("测试词".length(), dict.st_phrases.minLength);
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    @Test
    void testWithCustomDictFilesAppendReturnsCustomizedCopy() throws IOException {
        Path custom = Files.createTempFile("openccjava-postload-append-", ".txt");

        try {
            Files.write(
                    custom,
                    Collections.singletonList("测试词\t專用詞"),
                    StandardCharsets.UTF_8
            );

            DictionaryMaxlength base = DictionaryMaxlength.fromDicts();

            DictionaryMaxlength customized = base.withCustomDictFiles(
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Append
                            )
                    )
            );

            assertNotSame(base, customized);
            assertNull(base.st_phrases.dict.get("测试词"));
            assertEquals("專用詞", customized.st_phrases.dict.get("测试词"));
        } finally {
            Files.deleteIfExists(custom);
        }
    }

    @Test
    void testWithCustomDictFilesOverrideReturnsCustomizedCopy() throws IOException {
        Path custom = Files.createTempFile("openccjava-postload-override-", ".txt");

        try {
            Files.write(
                    custom,
                    Arrays.asList(
                            "测试词\t專用詞",
                            "测试鼠标\t測試滑鼠"
                    ),
                    StandardCharsets.UTF_8
            );

            DictionaryMaxlength base = DictionaryMaxlength.fromDicts();

            DictionaryMaxlength customized = base.withCustomDictFiles(
                    Collections.singletonList(
                            CustomDictSpec.fromFile(
                                    DictSlot.STPhrases,
                                    custom,
                                    CustomDictMode.Override
                            )
                    )
            );

            assertNotSame(base, customized);

            assertTrue(base.st_phrases.dict.size() > 2);
            assertEquals(2, customized.st_phrases.dict.size());

            assertEquals("專用詞", customized.st_phrases.dict.get("测试词"));
            assertEquals("測試滑鼠", customized.st_phrases.dict.get("测试鼠标"));

            assertEquals("测试鼠标".length(), customized.st_phrases.maxLength);
            assertEquals("测试词".length(), customized.st_phrases.minLength);
        } finally {
            Files.deleteIfExists(custom);
        }
    }
}
