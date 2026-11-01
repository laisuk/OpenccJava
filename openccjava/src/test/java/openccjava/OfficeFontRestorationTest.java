package openccjava;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class OfficeFontRestorationTest {
    @Test
    void restoresAdjacentKnownMarkersIncludingEmptyFontsAndPreservesUnknownTokens() throws IOException {
        String unknown = "__F_O_N_T_999__|__F_O_N_T_bad__|__F_O_N_T_01__|__F_O_N_T_2147483648__";
        assertEquals("FirstSecond|" + unknown + "|empty:", convert(
                xml("First", "Second", ""),
                text -> "__F_O_N_T_0____F_O_N_T_1__|" + unknown + "|empty:__F_O_N_T_2__", true));
    }

    @Test
    void leavesMarkerTextUnchangedWhenNoFontsWereMasked() throws IOException {
        String text = "__F_O_N_T_0__ __F_O_N_T_unknown__ __F_O_N_T_";
        assertEquals(text, convert(xml(), ignored -> text, true));
    }

    @Test
    void neverRecursivelyRestoresMarkerLikeFontValuesIncludingCyclesAndSelfReferences() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertEquals(
                "__F_O_N_T_1__|__F_O_N_T_0__|__F_O_N_T_2__",
                convert(xml("__F_O_N_T_1__", "__F_O_N_T_0__", "__F_O_N_T_2__"),
                        text -> "__F_O_N_T_0__|__F_O_N_T_1__|__F_O_N_T_2__", true)));
    }

    @Test
    void advancesPastMalformedPrefixesAndStillFindsLaterKnownMarkers() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertEquals(
                "__F_O_N_T_bad__|__F_O_N_T_unterminated First|tail__F_O_N_T_",
                convert(xml("First"), text ->
                        "__F_O_N_T_bad__|__F_O_N_T_unterminated __F_O_N_T_0__|tail__F_O_N_T_", true)));
    }

    @Test
    void preservesThousandsOfFontsAndEntitySpellingsWhileConvertingText() throws IOException {
        String[] fonts = new String[4096];
        for (int i = 0; i < fonts.length; i++) fonts[i] = "宋体" + i;
        String input = xml(fonts) + "头发发展 &nbsp; &#160; &#xA0; &unknown; &unterminated";
        String output = convert(input, text -> text.replace("头发发展", "頭髮發展"), true);
        assertEquals(input.replace("头发发展", "頭髮發展"), output);
        assertFalse(output.contains("__F_O_N_T_"));
    }

    @Test
    void leavesFontConversionEnabledWhenKeepFontIsFalse() throws IOException {
        String input = xml("宋体") + "__F_O_N_T_0__";
        assertEquals(input.replace("宋体", "宋體"), convert(
                input, text -> text.replace("宋体", "宋體"), false));
    }

    private static String xml(String... fonts) {
        StringBuilder xml = new StringBuilder("<w:document xmlns:w=\"urn:test\">");
        for (String font : fonts) xml.append("<w:rFonts w:ascii=\"").append(font).append("\"/>");
        return xml.append("</w:document>").toString();
    }

    private static String convert(String xml, TextConverter converter, boolean keepFont) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        OfficeHelper.MemoryResult result = OfficeHelper.convert(bytes.toByteArray(), "docx", converter, keepFont);
        assertTrue(result.success, result.message);
        assertNotNull(result.data);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(result.data))) {
            assertEquals("word/document.xml", zip.getNextEntry().getName());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = zip.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
