package openccjavawasm;

import openccjava.OpenCC;
import openccjava.OfficeHelper;
import java.nio.file.*;

/** JVM references for malformed-entity preservation and dense-font reproduction. */
public final class EntityReference {
    public static void main(String[] args) throws Exception {
        Path source = Paths.get(args[0]), output = Paths.get(args[1]);
        Files.createDirectories(output);
        OpenCC engine = new OpenCC("s2t");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(source, "*.epub")) {
            for (Path file : files) {
                byte[] input = Files.readAllBytes(file);
                for (boolean keep : new boolean[] {false, true}) {
                    long start = System.nanoTime();
                    OfficeHelper.MemoryResult result = OfficeHelper.convert(input, "epub", engine, false, keep);
                    if (!result.success) throw new AssertionError(result.message);
                    String name = file.getFileName().toString().replace(".epub", "-" + keep + ".epub");
                    Files.write(output.resolve(name), result.data);
                    System.out.println(name + " ms=" + (System.nanoTime()-start)/1e6);
                }
            }
        }
    }
}
