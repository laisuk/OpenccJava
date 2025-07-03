package openccjava;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

//import java.io.File;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryLibTest {

    static final String TEST_JSON_OUTPUT = "dicts/test_dictionary_maxlength.json";

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
        original.serializeToJson(TEST_JSON_OUTPUT);

        // Load again
        DictionaryMaxlength loaded = DictionaryMaxlength.fromJson(TEST_JSON_OUTPUT);

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

    @Test
    void testSerializeToJsonFromDicts() {
        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts();

        // Where to output
        String outputPath = "dictionary_maxlength.json";

        // Serialize
        assertDoesNotThrow(() -> dict.serializeToJson(outputPath));

        // Confirm file is written
        File file = new File(outputPath);
        assertTrue(file.exists(), "Serialized JSON file should exist");
        assertTrue(file.length() > 0, "Serialized file should not be empty");

        System.out.println("✅ dictionary_maxlength.json written at: " + file.getAbsolutePath());
    }

}
