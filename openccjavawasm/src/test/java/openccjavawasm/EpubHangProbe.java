package openccjavawasm;

import openccjava.OpenCC;
import openccjava.OfficeHelper;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;

/** Opt-in local book investigation; never copies book text into source fixtures. */
public final class EpubHangProbe {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
        OpenCC engine = new OpenCC("s2t");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                long start = System.nanoTime();
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = zip.read(buffer)) != -1) data.write(buffer, 0, count);
                System.out.println("inflate " + entry.getName() + " bytes=" + data.size() + " ms=" + (System.nanoTime()-start)/1e6);
                if (entry.getName().endsWith(".xhtml")) {
                    String text = new String(data.toByteArray(), StandardCharsets.UTF_8);
                    System.out.println("opencc begin " + entry.getName());
                    start = System.nanoTime(); engine.convert(text, false);
                    System.out.println("opencc end " + entry.getName() + " ms=" + (System.nanoTime()-start)/1e6);
                }
            }
        }
        for (boolean keep : new boolean[] {false, true}) {
            long start = System.nanoTime();
            System.out.println("OfficeHelper begin keepFont=" + keep);
            OfficeHelper.MemoryResult result = OfficeHelper.convert(bytes, "epub", engine, false, keep);
            System.out.println("OfficeHelper end success=" + result.success + " ms=" + (System.nanoTime()-start)/1e6 + " " + result.message);
        }
    }
}
