package openccjavawasm;

import openccjava.OpenCC;
import openccjava.OfficeHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Generates JVM semantic references from the same public in-memory API. */
public final class OfficeReference {
    public static void main(String[] args) throws Exception {
        Path fixtures = Paths.get(args[0]);
        Path output = fixtures.resolve("jvm");
        Files.createDirectories(output);
        for (String format : OfficeHelper.OFFICE_FORMATS) {
            byte[] input = Files.readAllBytes(fixtures.resolve("sample." + format));
            for (boolean keepFont : new boolean[] {false, true}) {
                for (boolean punctuation : new boolean[] {false, true}) {
                    String name = format + "-" + keepFont + "-" + punctuation + "." + format;
                    OfficeHelper.MemoryResult result = OfficeHelper.convert(input, format,
                            new OpenCC("s2t"), punctuation, keepFont);
                    if (!result.success) throw new AssertionError(name + ": " + result.message);
                    Files.write(output.resolve(name), result.data);
                }
            }
        }
        OfficeHelper.MemoryResult regional = OfficeHelper.convert(
                Files.readAllBytes(fixtures.resolve("sample.docx")), "docx", new OpenCC("s2twp"), true, true);
        if (!regional.success) throw new AssertionError(regional.message);
        Files.write(output.resolve("regional.docx"), regional.data);
        System.out.println("Generated 29 OfficeHelper JVM package references");
    }
}
