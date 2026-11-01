package openccjavawasm;

import openccjava.OpenCC;
import openccjava.OfficeHelper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Opt-in investigation only; reports entry names and timings, never book text. */
public final class EpubDiagnostics {
    private EpubDiagnostics() { }

    public static byte[] run(byte[] bytes, boolean keepFont, boolean identity, Consumer<String> log) throws IOException {
        long start = System.currentTimeMillis();
        log.accept("byte[] arrived bytes=" + bytes.length);
        log.accept("input ZIP validation begin"); BrowserZip.check(bytes, false);
        log.accept("input ZIP validation end ms=" + (System.currentTimeMillis() - start));
        List<String> targets = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                log.accept("inflate begin " + name);
                int read; long length = 0;
                while ((read = input.read(buffer)) != -1) length += read;
                log.accept("inflate end " + name + " bytes=" + length);
                if (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".opf") || name.endsWith(".ncx")) targets.add(name);
            }
        }
        OpenCC engine = new OpenCC("s2t");
        int[] index = {0};
        log.accept("OfficeHelper begin keepFont=" + keepFont + " identity=" + identity);
        OfficeHelper.MemoryResult result = OfficeHelper.convert(bytes, "epub", text -> {
            String name = targets.get(index[0]++);
            long before = System.currentTimeMillis();
            log.accept("converter begin " + name + " chars=" + text.length() + " elapsed=" + (before-start));
            String converted = identity ? text : engine.convert(text, false);
            log.accept("converter end " + name + " ms=" + (System.currentTimeMillis()-before));
            return converted;
        }, keepFont);
        log.accept("OfficeHelper end success=" + result.success + " elapsed=" + (System.currentTimeMillis()-start));
        if (!result.success) throw new IOException(result.message);
        BrowserZip.check(result.data, true);
        log.accept("output ZIP validation end elapsed=" + (System.currentTimeMillis()-start));
        return result.data;
    }
}
