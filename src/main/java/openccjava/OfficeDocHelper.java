package openccjava;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;
import java.util.stream.Stream;
import java.util.zip.*;

public class OfficeDocHelper {
    private static final Logger LOGGER = Logger.getLogger(OfficeDocHelper.class.getName());
    private static final List<String> OFFICE_FORMATS = Arrays.asList("docx", "xlsx", "pptx", "odt", "ods", "odp", "epub");

    public static class Result {
        public boolean success;
        public String message;

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static Result convert(File inputFile, File outputFile, String format, OpenCC converter, boolean punctuation, boolean keepFont) {
        String tempDirName = format + "_temp_" + UUID.randomUUID();
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve(tempDirName);

        try {
            unzip(inputFile.toPath(), tempDir);

            List<Path> targets = getTargetXmlPaths(format, tempDir);
            if (targets == null || targets.isEmpty()) {
                return new Result(false, "❌ Unsupported or invalid format: " + format);
            }

            int convertedCount = 0;
            for (Path relativePath : targets) {
                Path fullPath = tempDir.resolve(relativePath);
                if (!Files.isRegularFile(fullPath)) continue;

                String xml = Files.readString(fullPath);
                Map<String, String> fontMap = new HashMap<>();

                if (keepFont) {
                    Pattern pattern = getFontPattern(format);
                    if (pattern != null) {
                        Matcher matcher = pattern.matcher(xml);
                        int counter = 0;
                        StringBuilder sb = new StringBuilder();

                        while (matcher.find()) {
                            String marker = "__F_O_N_T_" + counter++ + "__";
                            fontMap.put(marker, matcher.group(2));
                            matcher.appendReplacement(sb, matcher.group(1) + marker + matcher.group(3));
                        }
                        matcher.appendTail(sb);
                        xml = sb.toString();
                    }
                }

                String converted = converter.convert(xml, punctuation);

                for (Map.Entry<String, String> entry : fontMap.entrySet()) {
                    converted = converted.replace(entry.getKey(), entry.getValue());
                }

                Files.writeString(fullPath, converted);
                convertedCount++;
            }

            if (convertedCount == 0) {
                return new Result(false, "⚠️ No valid XML fragments found in format: " + format);
            }

            if (Files.exists(outputFile.toPath())) Files.delete(outputFile.toPath());

            if ("epub".equals(format)) {
                return createEpubZip(tempDir, outputFile.toPath());
            } else {
                zip(tempDir.toFile(), outputFile);  // ✅ Both are File

            }

            return new Result(true, "✅ Successfully converted " + convertedCount + " fragment(s) in " + format + " document.");
        } catch (Exception ex) {
            return new Result(false, "❌ Conversion failed: " + ex.getMessage());
        } finally {
            deleteRecursive(tempDir.toFile());
        }
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName()).normalize();
                if (!newPath.startsWith(targetDir)) continue;
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void zip(File sourceDir, File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipRecursive(sourceDir, sourceDir, zos);
        }
    }

    private static void zipRecursive(File rootDir, File current, ZipOutputStream zos) throws IOException {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File file : files) {
            String entryName = rootDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");

            if (file.isDirectory()) {
                zipRecursive(rootDir, file, zos);
            } else {
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private static Result createEpubZip(Path sourceDir, Path outputZip) {
        Path mimePath = sourceDir.resolve("mimetype");

        if (!Files.exists(mimePath)) {
            return new Result(false, "❌ 'mimetype' file is missing. EPUB requires this.");
        }

        try (FileOutputStream fos = new FileOutputStream(outputZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            // Add mimetype first (uncompressed)
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            byte[] mimeBytes = Files.readAllBytes(mimePath);
            mimeEntry.setSize(mimeBytes.length);
            mimeEntry.setCompressedSize(mimeBytes.length);

            CRC32 crc = new CRC32();
            crc.update(mimeBytes);
            mimeEntry.setCrc(crc.getValue());

            zos.putNextEntry(mimeEntry);
            zos.write(mimeBytes);
            zos.closeEntry();

            // Add all other files
            try (Stream<Path> stream = Files.walk(sourceDir)) {
                stream
                        .filter(p -> Files.isRegularFile(p) && !p.equals(mimePath))
                        .forEach(p -> {
                            try {
                                String entryName = sourceDir.relativize(p).toString().replace("\\", "/");
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(p, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                // Optional: Log individual file errors
                            }
                        });
            }
            return new Result(true, "✅ EPUB archive created successfully.");
        } catch (Exception e) {
            return new Result(false, "❌ Failed to create EPUB: " + e.getMessage());
        }
    }

    private static List<Path> getTargetXmlPaths(String format, Path baseDir) {
        switch (format) {
            case "docx":
                return List.of(Paths.get("word/document.xml"));
            case "xlsx":
                return List.of(Paths.get("xl/sharedStrings.xml"));
            case "pptx": {
                List<Path> results = new ArrayList<>();
                File pptDir = baseDir.resolve("ppt").toFile();

                if (pptDir.isDirectory()) {
                    collectPptxTargets(pptDir, baseDir.toFile(), results);
                }

                return results;
            }
            case "odt":
            case "ods":
            case "odp":
                return List.of(Paths.get("content.xml"));
            case "epub": {
                List<Path> epubTargets = new ArrayList<>();
                File root = baseDir.toFile();

                if (root.isDirectory()) {
                    collectEpubTargets(root, baseDir.toFile(), epubTargets);
                }

                return epubTargets;
            }
            default:
                return null;
        }
    }

    private static void collectPptxTargets(File dir, File baseDir, List<Path> results) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectPptxTargets(file, baseDir, results);
            } else {
                String name = file.getName();
                if (name.endsWith(".xml") && (
                        name.startsWith("slide") ||
                                name.contains("notesSlide") ||
                                name.contains("slideMaster") ||
                                name.contains("slideLayout") ||
                                name.contains("comment")
                )) {
                    Path relative = baseDir.toPath().relativize(file.toPath());
                    results.add(relative);
                }
            }
        }
    }

    private static void collectEpubTargets(File current, File baseDir, List<Path> results) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectEpubTargets(file, baseDir, results);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".xhtml") || name.endsWith(".opf") || name.endsWith(".ncx")) {
                    results.add(baseDir.toPath().relativize(file.toPath()));
                }
            }
        }
    }

    private static Pattern getFontPattern(String format) {
        return switch (format) {
            case "docx" -> Pattern.compile("(w:(?:eastAsia|ascii|hAnsi|cs)=\")(.*?)(\")");
            case "xlsx" -> Pattern.compile("(val=\")(.*?)(\")");
            case "pptx" -> Pattern.compile("(typeface=\"\")(.*?)(\")");
            case "odt", "ods", "odp" ->
                    Pattern.compile("((?:style:font-name(?:-asian|-complex)?|svg:font-family|style:name)=[\"'])([^\"']+)([\"'])");
            case "epub" -> Pattern.compile("(font-family\\s*:\\s*)([^;\"']+)([;\"'])?");
            default -> null;
        };
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }

        if (!file.delete()) {
            System.err.println("⚠️ Failed to delete: " + file.getAbsolutePath());
        }
    }
}
