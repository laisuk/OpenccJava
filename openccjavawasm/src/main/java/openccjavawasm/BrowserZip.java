package openccjavawasm;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** ZIP32 boundary validation and TeaVM 0.16 UTF-8 filename flag repair only. */
final class BrowserZip {
    static final int MAX_INPUT = 64 * 1024 * 1024;
    private static final long MAX_EXPANDED = 256L * 1024 * 1024;

    private BrowserZip() { }

    static Set<String> check(byte[] bytes, boolean teavmOutput) {
        if (bytes == null || bytes.length < 22) fail("Invalid or empty ZIP package");
        if (bytes.length > MAX_INPUT) fail("ZIP package exceeds the 64 MiB browser limit");
        int end = -1;
        for (int i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
            if (u32(bytes, i) == 0x06054b50L && i + 22 + u16(bytes, i + 20) == bytes.length) {
                end = i; break;
            }
        }
        if (end < 0) fail("ZIP central directory end is missing or truncated");
        int count = u16(bytes, end + 10);
        if (u16(bytes, end + 4) != 0 || u16(bytes, end + 6) != 0
                || u16(bytes, end + 8) != count || count == 65535) fail("Split/ZIP64 packages are unsupported");
        long directory = u32(bytes, end + 16), size = u32(bytes, end + 12);
        if (directory + size != end) fail("Invalid ZIP central directory bounds (ZIP64 unsupported)");
        int cursor = (int) directory;
        long expanded = 0;
        Set<String> names = new HashSet<>();
        Map<String, long[]> expected = new HashMap<>();
        for (int i = 0; i < count; i++) {
            if (cursor + 46 > end || u32(bytes, cursor) != 0x02014b50L) fail("Invalid ZIP central directory entry");
            int flags = u16(bytes, cursor + 8), method = u16(bytes, cursor + 10);
            int nameLength = u16(bytes, cursor + 28);
            int next = cursor + 46 + nameLength + u16(bytes, cursor + 30) + u16(bytes, cursor + 32);
            if (next > end || u16(bytes, cursor + 34) != 0 || (flags & 1) != 0
                    || (method != 0 && method != 8)) fail("Unsupported encrypted/split/compressed ZIP entry");
            long compressed = u32(bytes, cursor + 20), length = u32(bytes, cursor + 24);
            expanded += length;
            if (expanded > MAX_EXPANDED) fail("ZIP expanded data exceeds the 256 MiB browser limit");
            long offset = u32(bytes, cursor + 42);
            if (offset + 30 > directory || u32(bytes, (int) offset) != 0x04034b50L) fail("Invalid ZIP local header offset");
            int local = (int) offset;
            if (u16(bytes, local + 26) != nameLength || u16(bytes, local + 8) != method
                    || u16(bytes, local + 6) != flags) fail("ZIP local/central headers disagree");
            long payload = offset + 30 + nameLength + u16(bytes, local + 28);
            if (payload + compressed > directory) fail("Truncated ZIP entry data");
            for (int n = 0; n < nameLength; n++) {
                if (bytes[local + 30 + n] != bytes[cursor + 46 + n]) fail("ZIP entry names disagree");
                if (!teavmOutput && bytes[cursor + 46 + n] < 0 && (flags & 0x800) == 0) {
                    fail("Non-ASCII ZIP names must be marked UTF-8 for this browser backend");
                }
            }
            String name = new String(bytes, cursor + 46, nameLength, StandardCharsets.UTF_8);
            byte[] encoded = name.getBytes(StandardCharsets.UTF_8);
            if (encoded.length != nameLength) fail("Invalid UTF-8 ZIP entry name");
            for (int n = 0; n < nameLength; n++) {
                if (encoded[n] != bytes[cursor + 46 + n]) fail("Invalid UTF-8 ZIP entry name");
            }
            if (!names.add(name)) fail("Duplicate ZIP entry: " + name);
            expected.put(name, new long[] {length, u32(bytes, cursor + 16)});
            if (teavmOutput) {
                // TZipOutputStream encodes names as UTF-8 but omits EFS in both headers.
                bytes[cursor + 9] |= 8;
                bytes[local + 7] |= 8;
            }
            cursor = next;
        }
        if (cursor != end || count == 0) fail("ZIP central directory count/size is invalid");
        // Independently reconcile inflated payloads with the central directory,
        // including actual expansion limits, rather than trusting declared sizes.
        long total = 0;
        int actualCount = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                long[] metadata = expected.get(entry.getName());
                if (metadata == null) fail("Unexpected ZIP local entry");
                CRC32 crc = new CRC32();
                long length = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    length += read; total += read;
                    if (total > MAX_EXPANDED) fail("ZIP expanded data exceeds the 256 MiB browser limit");
                    crc.update(buffer, 0, read);
                }
                input.closeEntry();
                if (length != metadata[0] || crc.getValue() != metadata[1]) fail("ZIP central directory size/CRC mismatch");
                actualCount++;
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid ZIP payload: " + error.getMessage(), error);
        }
        if (actualCount != count) fail("ZIP local/central entry count mismatch");
        return names;
    }

    private static int u16(byte[] b, int p) { return (b[p] & 255) | ((b[p + 1] & 255) << 8); }
    private static long u32(byte[] b, int p) { return (u16(b, p) & 65535L) | ((long) u16(b, p + 2) << 16); }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
