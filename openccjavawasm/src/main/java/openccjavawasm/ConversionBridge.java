package openccjavawasm;

import openccjava.OpenCC;
import openccjava.CustomDictSpec;
import openccjava.CustomDictMode;
import openccjava.DictSlot;
import openccjava.DictionaryMaxlength;
import java.util.*;
import openccjava.OpenccConfig;
import openccjava.DeTofu;
import openccjava.OfficeHelper;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSBody;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.Locale;

/** Experimental synchronous browser bridge. Errors are exposed as plain strings. */
public final class ConversionBridge {
    private static OpenCC engine;
    private static String lastError = "";
    private static String dictionaryStatus = "Default";

    private ConversionBridge() { }

    @JSExport
    public static String getVersion() { return BuildInfo.VERSION; }

    @JSExport
    public static boolean initialize() {
        lastError = "";
        try {
            if (engine == null) {
                engine = new OpenCC("s2t");
            }
            return true;
        } catch (Throwable error) {
            lastError = error.toString();
            return false;
        }
    }

    @JSExport
    public static String[] getSupportedDictionarySlots() {
        return DictSlot.supportedCanonicalNames().toArray(new String[0]);
    }

    @JSExport
    public static String getDictionaryStatus() { return dictionaryStatus; }

    /** Only portable strings cross the boundary; Core parses and patches the dictionaries. */
    @JSExport
    public static boolean applyCustomDictionaries(String[] slots, String[] modes, String[] texts, String config) {
        lastError = "";
        try {
            if (!initialize()) return false;
            OpenccConfig parsed = OpenccConfig.tryParse(config);
            if (parsed == null) throw new IllegalArgumentException("Unsupported configuration: " + config);
            if (slots == null || modes == null || texts == null || slots.length == 0
                    || slots.length != modes.length || slots.length != texts.length)
                throw new IllegalArgumentException("Invalid dictionary arrays");
            List<CustomDictSpec> specs = new ArrayList<>();
            Set<DictSlot> seen = new HashSet<>();
            int entries = 0;
            for (int i = 0; i < slots.length; i++) {
                try {
                    DictSlot slot = DictSlot.parse(slots[i]);
                    if (!seen.add(slot)) throw new IllegalArgumentException("duplicate slot: " + slots[i]);
                    CustomDictMode mode;
                    if ("append".equals(modes[i])) mode = CustomDictMode.Append;
                    else if ("override".equals(modes[i])) mode = CustomDictMode.Override;
                    else throw new IllegalArgumentException("Invalid dictionary mode");
                    Map<String, String> pairs = DictionaryMaxlength.parseDictionaryPairs(texts[i]);
                    if (pairs.isEmpty()) throw new IllegalArgumentException("no usable entries");
                    entries += pairs.size();
                    specs.add(CustomDictSpec.fromPairs(slot, pairs, mode));
                } catch (RuntimeException error) {
                    throw new IllegalArgumentException("Card " + (i + 1) + ": " + error.getMessage(), error);
                }
            }
            // This constructor always copies the original shared base, never the active snapshot.
            OpenCC replacement = new OpenCC(parsed, specs);
            String status = "Custom (" + slots.length + (slots.length == 1 ? " slot - " : " slots - ")
                    + entries + (entries == 1 ? " entry)" : " entries)");
            engine = replacement;
            dictionaryStatus = status;
            return true;
        } catch (Throwable error) { lastError = error.toString(); return false; }
    }

    @JSExport
    public static boolean resetDictionaries(String config) {
        lastError = "";
        try {
            OpenccConfig parsed = OpenccConfig.tryParse(config);
            if (parsed == null) throw new IllegalArgumentException("Unsupported configuration: " + config);
            OpenCC replacement = new OpenCC(parsed);
            engine = replacement;
            dictionaryStatus = "Default";
            return true;
        } catch (Throwable error) { lastError = error.toString(); return false; }
    }

    @JSExport
    public static String convert(String text, String config, boolean punctuation) {
        lastError = "";
        try {
            if (text == null) throw new IllegalArgumentException("Text must not be null");
            OpenccConfig parsed = OpenccConfig.tryParse(config);
            if (parsed == null) throw new IllegalArgumentException("Unsupported configuration: " + config);
            if (!initialize()) return null;
            engine.setConfig(parsed);
            return engine.convert(text, punctuation);
        } catch (Throwable error) {
            lastError = error.toString();
            return null;
        }
    }

    @JSExport
    public static String getLastError() { return lastError; }

    @JSExport
    public static byte[] diagnoseEpub(byte[] input, boolean keepFont, boolean identity) {
        lastError = "";
        try { return EpubDiagnostics.run(input, keepFont, identity, ConversionBridge::diagnosticLog); }
        catch (Throwable error) { lastError = error.toString(); return null; }
    }

    @JSBody(params = "message", script = "console.log('[epub-diag] ' + message);")
    private static native void diagnosticLog(String message);

    @JSExport
    public static String diagnosticZipText(byte[] bytes, String name) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192]; int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            return null;
        } catch (Exception error) { lastError = error.toString(); return null; }
    }

    /** Focused real DEFLATE round-trip diagnostic used by the browser suite. */
    @JSExport
    public static byte[] roundTripZip(byte[] input) {
        lastError = "";
        try {
            BrowserZip.check(input, false);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipInputStream source = new ZipInputStream(new ByteArrayInputStream(input));
                 ZipOutputStream target = new ZipOutputStream(output)) {
                ZipEntry entry;
                int count = 0;
                byte[] buffer = new byte[8192];
                while ((entry = source.getNextEntry()) != null) {
                    target.putNextEntry(new ZipEntry(entry.getName()));
                    int length;
                    while ((length = source.read(buffer)) != -1) target.write(buffer, 0, length);
                    target.closeEntry();
                    source.closeEntry();
                    count++;
                }
                if (count == 0) throw new IllegalArgumentException("ZIP contains no entries");
            }
            byte[] rebuilt = output.toByteArray();
            BrowserZip.check(rebuilt, true);
            return rebuilt;
        } catch (Throwable error) { lastError = error.toString(); return null; }
    }

    @JSExport
    public static byte[] convertOfficeBytes(byte[] input, String format, String config,
                                             boolean punctuation, boolean keepFont) {
        lastError = "";
        try {
            String normalizedFormat = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
            if (!OfficeHelper.OFFICE_FORMATS.contains(normalizedFormat)) {
                throw new IllegalArgumentException("Unsupported document format: " + format);
            }
            for (String entryName : BrowserZip.check(input, false)) {
                if ("epub".equals(normalizedFormat)) {
                    for (int i = 0; i < entryName.length(); i++) {
                        if (entryName.charAt(i) > 127) throw new IllegalArgumentException(
                                "EPUB internal entry names must be ASCII: OfficeHelper may convert XML paths without renaming entries");
                    }
                }
            }
            OpenccConfig parsed = OpenccConfig.tryParse(config);
            if (parsed == null) throw new IllegalArgumentException("Unsupported configuration: " + config);
            if (!initialize()) return null;
            engine.setConfig(parsed);
            OfficeHelper.MemoryResult result = OfficeHelper.convert(input, normalizedFormat, engine, punctuation, keepFont);
            if (!result.success || result.data == null) throw new IllegalArgumentException(result.message);
            BrowserZip.check(result.data, true);
            return result.data;
        } catch (Throwable error) { lastError = error.toString(); return null; }
    }

    @JSExport
    public static String[] getSupportedConfigs() {
        return OpenccConfig.supportedCanonicalNames().toArray(new String[0]);
    }

    @JSExport
    public static boolean isValidConfig(String config) {
        return OpenccConfig.tryParse(config) != null;
    }

    @JSExport
    public static String normalizeCompatExtended(String text) {
        if (!initialize()) return null;
        try { return engine.normalizeCompatExtended(text); }
        catch (Throwable error) { lastError = error.toString(); return null; }
    }

    @JSExport
    public static String detofu(String text) {
        if (!initialize()) return null;
        try { return engine.deTofu(text, DeTofu.Level.ExtB); }
        catch (Throwable error) { lastError = error.toString(); return null; }
    }

    @JSExport
    public static int zhoCheck(String text) {
        if (!initialize()) return -1;
        try { return OpenCC.zhoCheck(text); }
        catch (Throwable error) { lastError = error.toString(); return -1; }
    }
}
