package openccjava;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

//import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DictionaryMaxlengthNoDepsTest {

    private static final String SAMPLE_JSON =
            "{\n" +
                    "  \"st_characters\": [ { \"汉\": \"漢\", \"发\": \"發\" }, 1 ],\n" +
                    "  \"st_phrases\":    [ { \"后台\": \"後台\" }, 2 ],\n" +
                    "  \"jps_characters\":[ { \"芸\": \"藝\" }, 1 ],\n" +
                    "  \"unknown_block\": [ { \"x\": \"y\" }, 9 ]\n" +
                    "}";

    private static final String RESOURCE_PATH = "/dicts/dictionary_maxlength.json";

    @Test
    void loadsFromStringNoDeps() {
        DictionaryMaxlength d = DictionaryMaxlength.fromJsonStringNoDeps(SAMPLE_JSON);

        assertNotNull(d.st_characters);
        assertEquals(1, d.st_characters.maxLength);
        Map<String, String> expectedStChars = new LinkedHashMap<>();
        expectedStChars.put("汉", "漢");
        expectedStChars.put("发", "發");
        assertEquals(expectedStChars, d.st_characters.dict);

        assertNotNull(d.st_phrases);
        assertEquals(2, d.st_phrases.maxLength);
        assertEquals(Collections.singletonMap("后台", "後台"), d.st_phrases.dict);

        assertNotNull(d.jps_characters);
        assertEquals(1, d.jps_characters.maxLength);
        assertEquals(Collections.singletonMap("芸", "藝"), d.jps_characters.dict);

        // Unknown key ignored — no assertion needed for unknown_block
    }

    @Test
    void loadsFromInputStreamNoDeps() throws Exception {
        byte[] bytes = SAMPLE_JSON.getBytes(StandardCharsets.UTF_8);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            DictionaryMaxlength d = DictionaryMaxlength.fromJsonNoDeps(in);
            assertEquals("漢", d.st_characters.dict.get("汉"));
            assertEquals(2, d.st_phrases.maxLength);
        }
    }

    @Test
    void loadsFromPathNoDeps(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("dictionary_maxlength.json");
//        Files.writeString(json, SAMPLE_JSON, StandardCharsets.UTF_8);
        Files.write(json, SAMPLE_JSON.getBytes(StandardCharsets.UTF_8));

        DictionaryMaxlength d = DictionaryMaxlength.fromJsonNoDeps(json);

        assertEquals("後台", d.st_phrases.dict.get("后台"));
        assertEquals(1, d.jps_characters.maxLength);
        assertNull(d.jp_variants); // not provided in sample JSON
    }

    @Test
    void loadsRealSnapshotFromClasspath() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/dicts/dictionary_maxlength.json")) {
            assertNotNull(in, "Test resource dicts/dictionary_maxlength.json is missing");
            DictionaryMaxlength d = DictionaryMaxlength.fromJsonNoDeps(in);

            // Sanity checks on a few well-known buckets
            assertNotNull(d.st_characters);
            assertTrue(d.st_characters.maxLength > 0);
            assertFalse(d.st_characters.dict.isEmpty());

            assertNotNull(d.st_phrases);
            assertTrue(d.st_phrases.maxLength > 1);
            assertFalse(d.st_phrases.dict.isEmpty());

            assertNotNull(d.jps_characters);
            assertFalse(d.jps_characters.dict.isEmpty());

            // Spot-check a known mapping if you’re comfortable hard-coding it:
            assertEquals("後", d.st_characters.dict.get("后"));
        }
    }

    @Test
    void roundTrip_viaClasspath(@TempDir Path tmp) throws Exception {
        // 1) Load real snapshot using the no-deps parser
        byte[] original;
        try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Missing test resource " + RESOURCE_PATH);

            // Java 8 replacement for InputStream.readAllBytes()
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmpBuf = new byte[8192];
            int n;
            while ((n = in.read(tmpBuf)) != -1) {
                buffer.write(tmpBuf, 0, n);
            }
            original = buffer.toByteArray();
        }

        DictionaryMaxlength a = DictionaryMaxlength.fromJsonNoDeps(new ByteArrayInputStream(original));
        // 2) Serialize back to JSON
        Path out = tmp.resolve("rt.json");
        a.serializeToJsonNoDeps(out);
        // 3) Load the emitted JSON again with the no-deps parser
        DictionaryMaxlength b = DictionaryMaxlength.fromJsonNoDeps(out);
        // 4) Deep-compare all dict entries
        assertDictionariesEqual(a, b);
    }

    @Test
    void roundTrip_viaString() throws Exception {
        // Load via no-deps
        String json;
        try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Missing test resource " + RESOURCE_PATH);

            // Java 8 replacement for InputStream.readAllBytes()
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmpBuf = new byte[8192];
            int n;
            while ((n = in.read(tmpBuf)) != -1) {
                buffer.write(tmpBuf, 0, n);
            }

            json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }

        DictionaryMaxlength a = DictionaryMaxlength.fromJsonStringNoDeps(json);
        // Serialize to a String
        String emitted = a.serializeToJsonStringNoDepsCompact();
        // Reload via no-deps
        DictionaryMaxlength b = DictionaryMaxlength.fromJsonStringNoDeps(emitted);
        // Compare
        assertDictionariesEqual(a, b);
    }

    // ---- helpers ----

    private static void assertDictionariesEqual(DictionaryMaxlength a, DictionaryMaxlength b) {
        // compare each DictEntry field (no reflection in production code; tests are fine being explicit)
        compare("st_characters", a.st_characters, b.st_characters);
        compare("st_phrases", a.st_phrases, b.st_phrases);
        compare("ts_characters", a.ts_characters, b.ts_characters);
        compare("ts_phrases", a.ts_phrases, b.ts_phrases);

        compare("tw_phrases", a.tw_phrases, b.tw_phrases);
        compare("tw_phrases_rev", a.tw_phrases_rev, b.tw_phrases_rev);
        compare("tw_variants", a.tw_variants, b.tw_variants);
        compare("tw_variants_rev", a.tw_variants_rev, b.tw_variants_rev);
        compare("tw_variants_rev_phrases", a.tw_variants_rev_phrases, b.tw_variants_rev_phrases);

        compare("hk_variants", a.hk_variants, b.hk_variants);
        compare("hk_variants_rev", a.hk_variants_rev, b.hk_variants_rev);
        compare("hk_variants_rev_phrases", a.hk_variants_rev_phrases, b.hk_variants_rev_phrases);

        compare("jps_characters", a.jps_characters, b.jps_characters);
        compare("jps_phrases", a.jps_phrases, b.jps_phrases);
        compare("jp_variants", a.jp_variants, b.jp_variants);
        compare("jp_variants_rev", a.jp_variants_rev, b.jp_variants_rev);
    }

    private static void compare(String name, DictionaryMaxlength.DictEntry x, DictionaryMaxlength.DictEntry y) {
        if (x == null && y == null) return;
        assertNotNull(x, name + " is null on left but not on right");
        assertNotNull(y, name + " is null on right but not on left");
        assertEquals(x.maxLength, y.maxLength, name + ".maxLength");
        assertEquals(x.dict, y.dict, name + ".dict");
    }
}
