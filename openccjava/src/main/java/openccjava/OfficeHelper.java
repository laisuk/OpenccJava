package openccjava;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for converting Office-based document formats using OpenCC logic.
 *
 * <p>Supported formats include:
 * <ul>
 *   <li>Microsoft Office XML formats: {@code .docx}, {@code .xlsx}, {@code .pptx}</li>
 *   <li>OpenDocument formats: {@code .odt}, {@code .ods}, {@code .odp}</li>
 *   <li>EPUB eBooks: {@code .epub}</li>
 * </ul>
 *
 * <p>Internally, the class handles these formats as ZIP archives, extracts and processes
 * their XML/XHTML content, applies OpenCC transformations, and repackages the result.
 *
 * <p>This class is designed for use in batch or CLI applications.
 */
public class OfficeHelper {
    /**
     * Unmodifiable list of supported file extensions for Office and EPUB documents.
     */
    public static final List<String> OFFICE_FORMATS = Collections.unmodifiableList(
            Arrays.asList("docx", "xlsx", "pptx", "odt", "ods", "odp", "epub")
    );

    /**
     * Logger instance used for reporting non-fatal processing errors.
     */
    private static final Logger LOGGER = Logger.getLogger(OfficeHelper.class.getName());

    /**
     * Precompiled regular expression patterns for extracting font declarations
     * across supported document formats.
     *
     * <p>Each pattern provides three capturing groups:
     * <ol>
     *   <li>Prefix (e.g., attribute or CSS property start)</li>
     *   <li>The actual font value</li>
     *   <li>Suffix (e.g., closing quote, semicolon, or delimiter)</li>
     * </ol>
     *
     * <p>Supported formats and their corresponding attributes:
     * <ul>
     *   <li><b>docx</b>: {@code w:eastAsia}, {@code w:ascii}, {@code w:hAnsi}, {@code w:cs}</li>
     *   <li><b>xlsx</b>: {@code val}</li>
     *   <li><b>pptx</b>: {@code typeface}</li>
     *   <li><b>odt/ods/odp</b>: {@code style:font-name}, {@code style:font-name-asian},
     *       {@code style:font-name-complex}, {@code svg:font-family}, {@code style:name}</li>
     *   <li><b>epub</b>: CSS {@code font-family}</li>
     * </ul>
     *
     * <p>These patterns are used when {@code --keep-font} is enabled to temporarily
     * replace font declarations with markers during OpenCC text conversion,
     * and then restore them afterward.
     */
    private static final Map<String, Pattern> FONT_PATTERNS;

    /**
     * Matches an XLSX inline-string cell:
     * {@code <c ... t="inlineStr" ...>...</c>}
     */
    private static final Pattern XLSX_INLINE_STRING_CELL_PATTERN = Pattern.compile(
            "<c\\b(?=[^>]*\\bt=(?:\"inlineStr\"|'inlineStr'))[^>]*>.*?</c>",
            Pattern.DOTALL
    );

    /**
     * Matches text nodes inside inline-string content:
     * {@code <t ...>TEXT</t>}
     */
    private static final Pattern XLSX_TEXT_NODE_PATTERN = Pattern.compile(
            "(<t\\b[^>]*>)(.*?)(</t>)",
            Pattern.DOTALL
    );

    static {
        Map<String, Pattern> map = new HashMap<>();
        map.put("docx", Pattern.compile("(w:(?:eastAsia|ascii|hAnsi|cs)=\")(.*?)(\")"));
        map.put("xlsx", Pattern.compile("(val=\")(.*?)(\")"));
        map.put("pptx", Pattern.compile("(typeface=\")(.*?)(\")"));

        Pattern odPattern = Pattern.compile(
                "((?:style:font-name(?:-asian|-complex)?|svg:font-family|style:name)=[\"'])([^\"']+)([\"'])"
        );
        map.put("odt", odPattern);
        map.put("ods", odPattern);
        map.put("odp", odPattern);

        map.put("epub", Pattern.compile("(font-family\\s*:\\s*)([^;\"']+)([;\"'])?"));

        FONT_PATTERNS = Collections.unmodifiableMap(map);
    }

    /**
     * Base type for Office/EPUB conversion results.
     *
     * <p>This abstract class represents the outcome of a conversion operation.
     * Subclasses provide additional details depending on whether the conversion
     * was performed on files ({@link FileResult}) or in-memory data
     * ({@link MemoryResult}).</p>
     *
     * <p>The {@code success} flag indicates whether the conversion completed
     * without errors, while {@code message} contains any accompanying description,
     * such as warnings, error information, or status notes.</p>
     */
    public abstract static class Result {
        /**
         * Indicates whether the conversion succeeded.
         * <p>
         * A value of {@code true} means the conversion completed normally.
         * A value of {@code false} typically indicates a failure or that
         * the operation was skipped due to unsupported format or invalid input.
         * </p>
         */
        public final boolean success;

        /**
         * Descriptive message associated with the conversion result.
         * <p>
         * May contain an informational note, a warning description,
         * or a detailed failure explanation. Never {@code null}.
         * </p>
         */
        public final String message;

        /**
         * Creates a new result instance.
         *
         * @param success whether the conversion succeeded
         * @param message descriptive message explaining the result; must not be {@code null}
         * @throws NullPointerException if {@code message} is {@code null}
         */
        protected Result(boolean success, String message) {
            this.success = success;
            this.message = Objects.requireNonNull(message, "message must not be null");
        }
    }

    /**
     * Result for file-based conversions that do not expose an in-memory payload.
     */
    public static final class FileResult extends Result {
        /**
         * Creates a {@code FileResult}.
         *
         * @param success true if the conversion succeeded, false otherwise
         * @param message the result message or error description; must not be {@code null}
         * @throws NullPointerException if {@code message} is {@code null}
         */
        public FileResult(boolean success, String message) {
            super(success, message);
        }
    }

    /**
     * Result for in-memory conversions that expose converted document bytes.
     */
    public static final class MemoryResult extends Result {
        /**
         * Converted document bytes (e.g., a DOCX/EPUB ZIP).
         */
        public final byte[] data;

        /**
         * Creates a {@code MemoryResult}.
         *
         * @param success true if the conversion succeeded, false otherwise
         * @param message the result message or error description; must not be {@code null}
         * @param data    converted document bytes; defensively copied, or {@code null}
         * @throws NullPointerException if {@code message} is {@code null}
         */
        public MemoryResult(boolean success, String message, byte[] data) {
            super(success, message);
            this.data = data == null ? null : data.clone();
        }
    }

    /**
     * Constructs an instance of {@code OfficeHelper}.
     */
    public OfficeHelper() {
        // No initialization required
    }

    /**
     * Converts an Office or EPUB document entirely in memory.
     *
     * <p>The input ZIP package is read directly from {@code inputBytes}. Unchanged
     * entries are streamed into a new in-memory ZIP, while only selected text-bearing
     * XML/XHTML entries are materialized as UTF-8 strings for OpenCC conversion.
     * No temporary directory or temporary package file is created.</p>
     *
     * <p>For EPUB, the {@code mimetype} entry is emitted first and stored without
     * compression as required by the EPUB container specification.</p>
     *
     * @param inputBytes  the complete Office/EPUB package bytes
     * @param format      logical format name ({@code docx/xlsx/pptx/odt/ods/odp/epub})
     * @param converter   OpenCC converter
     * @param punctuation whether punctuation conversion is enabled
     * @param keepFont    whether supported font declarations should be preserved
     * @return conversion result containing the rebuilt package bytes on success
     */
    public static MemoryResult convert(
            byte[] inputBytes,
            String format,
            OpenCC converter,
            boolean punctuation,
            boolean keepFont
    ) {
        if (inputBytes == null || inputBytes.length == 0) {
            return new MemoryResult(false, "❌ Input bytes are empty.", null);
        }
        if (converter == null) {
            return new MemoryResult(false, "❌ Converter must not be null.", null);
        }

        String normalizedFormat = normalizeFormat(format);
        if (normalizedFormat == null) {
            return new MemoryResult(false, "❌ Unsupported or invalid format: " + format, null);
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(8192, inputBytes.length));
            int convertedCount;

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(output))) {
                if ("epub".equals(normalizedFormat)) {
                    byte[] mimetype = findEntryBytes(
                            new ByteArrayInputStream(inputBytes),
                            "mimetype"
                    );
                    if (mimetype == null) {
                        return new MemoryResult(false, "❌ 'mimetype' file is missing. EPUB requires this.", null);
                    }
                    writeStoredEntry(zos, "mimetype", mimetype);
                }

                try (ZipInputStream zis = new ZipInputStream(
                        new BufferedInputStream(new ByteArrayInputStream(inputBytes)))) {
                    convertedCount = convertArchive(
                            zis,
                            zos,
                            normalizedFormat,
                            converter,
                            punctuation,
                            keepFont,
                            "epub".equals(normalizedFormat)
                    );
                }
            }

            if (convertedCount == 0) {
                return new MemoryResult(
                        false,
                        "⚠️ No valid XML fragments found in format: " + normalizedFormat,
                        null
                );
            }

            return new MemoryResult(
                    true,
                    successMessage(convertedCount, normalizedFormat),
                    output.toByteArray()
            );
        } catch (Exception ex) {
            return new MemoryResult(false, "❌ Conversion failed: " + ex.getMessage(), null);
        }
    }

    /**
     * Converts an Office or EPUB document using a streaming file-to-file path.
     *
     * <p>The complete source package is never read into a {@code byte[]}. Unchanged
     * ZIP entries stream directly from the input file to the rebuilt package; only
     * selected XML/XHTML entries are buffered for text conversion. The rebuilt package
     * is first written to a sibling temporary file and then published to
     * {@code outputFile} after successful conversion.</p>
     *
     * @param inputFile   source Office/EPUB package
     * @param outputFile  destination package
     * @param format      logical format name
     * @param converter   OpenCC converter
     * @param punctuation whether punctuation conversion is enabled
     * @param keepFont    whether supported font declarations should be preserved
     * @return file conversion result
     */
    public static FileResult convert(
            File inputFile,
            File outputFile,
            String format,
            OpenCC converter,
            boolean punctuation,
            boolean keepFont
    ) {
        if (inputFile == null || !inputFile.isFile()) {
            return new FileResult(false, "❌ Input file must exist and be a regular file.");
        }
        if (outputFile == null) {
            return new FileResult(false, "❌ Output file must not be null.");
        }
        if (converter == null) {
            return new FileResult(false, "❌ Converter must not be null.");
        }

        String normalizedFormat = normalizeFormat(format);
        if (normalizedFormat == null) {
            return new FileResult(false, "❌ Unsupported or invalid format: " + format);
        }

        Path outputPath = outputFile.toPath().toAbsolutePath();
        Path parent = outputPath.getParent();
        Path tempOutput = null;

        try {
            if (parent != null) {
                Files.createDirectories(parent);
                tempOutput = Files.createTempFile(parent, outputFile.getName() + ".", ".tmp");
            } else {
                tempOutput = Files.createTempFile(outputFile.getName() + ".", ".tmp");
            }

            int convertedCount;
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(tempOutput.toFile().toPath())))) {

                if ("epub".equals(normalizedFormat)) {
                    byte[] mimetype;
                    try (InputStream mimeInput = new BufferedInputStream(Files.newInputStream(inputFile.toPath()))) {
                        mimetype = findEntryBytes(mimeInput, "mimetype");
                    }
                    if (mimetype == null) {
                        return new FileResult(false, "❌ 'mimetype' file is missing. EPUB requires this.");
                    }
                    writeStoredEntry(zos, "mimetype", mimetype);
                }

                try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
                        Files.newInputStream(inputFile.toPath())))) {
                    convertedCount = convertArchive(
                            zis,
                            zos,
                            normalizedFormat,
                            converter,
                            punctuation,
                            keepFont,
                            "epub".equals(normalizedFormat)
                    );
                }
            }

            if (convertedCount == 0) {
                return new FileResult(
                        false,
                        "⚠️ No valid XML fragments found in format: " + normalizedFormat
                );
            }

            try {
                Files.move(
                        tempOutput,
                        outputPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException atomicMoveFailure) {
                Files.move(tempOutput, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
            tempOutput = null;

            return new FileResult(true, successMessage(convertedCount, normalizedFormat));
        } catch (IOException ex) {
            return new FileResult(false, "❌ I/O error during conversion: " + ex.getMessage());
        } catch (Exception ex) {
            return new FileResult(false, "❌ Conversion failed: " + ex.getMessage());
        } finally {
            if (tempOutput != null) {
                try {
                    Files.deleteIfExists(tempOutput);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to delete temporary output " + tempOutput, e);
                }
            }
        }
    }

    /**
     * Streams one ZIP archive into another, converting only text-bearing entries.
     *
     * @param skipEpubMimetype whether an already-emitted EPUB {@code mimetype} entry should be skipped
     * @return number of converted text-bearing entries
     */
    private static int convertArchive(
            ZipInputStream zis,
            ZipOutputStream zos,
            String format,
            OpenCC converter,
            boolean punctuation,
            boolean keepFont,
            boolean skipEpubMimetype
    ) throws IOException {
        int convertedCount = 0;
        ZipEntry sourceEntry;

        while ((sourceEntry = zis.getNextEntry()) != null) {
            String entryName = normalizeEntryName(sourceEntry.getName());

            if (skipEpubMimetype && "mimetype".equals(entryName)) {
                zis.closeEntry();
                continue;
            }

            if (sourceEntry.isDirectory()) {
                ZipEntry outputEntry = new ZipEntry(entryName.endsWith("/") ? entryName : entryName + "/");
                copyEntryMetadata(sourceEntry, outputEntry);
                zos.putNextEntry(outputEntry);
                zos.closeEntry();
                zis.closeEntry();
                continue;
            }

            boolean target = isTargetEntry(format, entryName);
            ZipEntry outputEntry = new ZipEntry(entryName);
            copyEntryMetadata(sourceEntry, outputEntry);
            zos.putNextEntry(outputEntry);

            if (target) {
                byte[] bytes = readCurrentEntry(zis);
                String xml = new String(bytes, StandardCharsets.UTF_8);
                String converted = convertTextEntry(
                        format,
                        entryName,
                        xml,
                        converter,
                        punctuation,
                        keepFont
                );
                zos.write(converted.getBytes(StandardCharsets.UTF_8));
                convertedCount++;
            } else {
                copy(zis, zos);
            }

            zos.closeEntry();
            zis.closeEntry();
        }

        return convertedCount;
    }

    /**
     * Applies font masking and format-specific conversion to one text-bearing entry.
     */
    private static String convertTextEntry(
            String format,
            String entryName,
            String xml,
            OpenCC converter,
            boolean punctuation,
            boolean keepFont
    ) {
        Map<String, String> fontMap = new HashMap<>();

        Path relativePath = Paths.get(entryName);
        if (keepFont && shouldMaskFonts(format, relativePath)) {
            Pattern pattern = getFontPattern(format);
            if (pattern != null) {
                Matcher matcher = pattern.matcher(xml);
                int counter = 0;
                StringBuffer sb = new StringBuffer();

                while (matcher.find()) {
                    String marker = "__F_O_N_T_" + counter++ + "__";
                    fontMap.put(marker, matcher.group(2));

                    String group3 = matcher.groupCount() >= 3 && matcher.group(3) != null
                            ? matcher.group(3)
                            : "";

                    matcher.appendReplacement(
                            sb,
                            Matcher.quoteReplacement(matcher.group(1) + marker + group3)
                    );
                }
                matcher.appendTail(sb);
                xml = sb.toString();
            }
        }

        String converted = convertXmlContent(
                format,
                relativePath,
                xml,
                converter,
                punctuation
        );
        if (converted == null) {
            throw new IllegalStateException("native error: " + converter.getLastError());
        }

        for (Map.Entry<String, String> entry : fontMap.entrySet()) {
            converted = converted.replace(entry.getKey(), entry.getValue());
        }
        return converted;
    }

    /**
     * Returns whether a ZIP entry contains text that should be converted.
     */
    private static boolean isTargetEntry(String format, String entryName) {
        switch (format) {
            case "docx":
                return "word/document.xml".equals(entryName);

            case "xlsx":
                return "xl/sharedStrings.xml".equals(entryName)
                        || (entryName.startsWith("xl/worksheets/") && entryName.endsWith(".xml"));

            case "pptx": {
                if (!entryName.startsWith("ppt/") || !entryName.endsWith(".xml")) {
                    return false;
                }
                int slash = entryName.lastIndexOf('/');
                String name = slash >= 0 ? entryName.substring(slash + 1) : entryName;
                return name.startsWith("slide")
                        || name.contains("notesSlide")
                        || name.contains("slideMaster")
                        || name.contains("slideLayout")
                        || name.contains("comment");
            }

            case "odt":
            case "ods":
            case "odp":
                return "content.xml".equals(entryName);

            case "epub": {
                String lower = entryName.toLowerCase(Locale.ROOT);
                return lower.endsWith(".xhtml")
                        || lower.endsWith(".html")
                        || lower.endsWith(".opf")
                        || lower.endsWith(".ncx");
            }

            default:
                return false;
        }
    }

    /**
     * Normalizes and validates a logical format name.
     */
    private static String normalizeFormat(String format) {
        if (format == null) {
            return null;
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return OFFICE_FORMATS.contains(normalized) ? normalized : null;
    }

    private static String normalizeEntryName(String name) {
        return name == null ? "" : name.replace('\\', '/');
    }

    /**
     * Copies safe ZIP metadata that does not constrain output compression sizes/CRC.
     */
    private static void copyEntryMetadata(ZipEntry source, ZipEntry target) {
        if (source.getTime() >= 0) {
            target.setTime(source.getTime());
        }
        if (source.getComment() != null) {
            target.setComment(source.getComment());
        }
        byte[] extra = source.getExtra();
        if (extra != null) {
            target.setExtra(extra);
        }
    }

    /**
     * Finds and returns one ZIP entry's bytes. The supplied package stream is consumed.
     */
    private static byte[] findEntryBytes(InputStream packageInput, String wantedName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(packageInput)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && wantedName.equals(normalizeEntryName(entry.getName()))) {
                    return readCurrentEntry(zis);
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    /**
     * Writes an uncompressed ZIP entry with the CRC/size fields required by STORED entries.
     */
    private static void writeStoredEntry(ZipOutputStream zos, String entryName, byte[] data)
            throws IOException {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);

        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());

        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] readCurrentEntry(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static String successMessage(int convertedCount, String format) {
        return "✅ Successfully converted " + convertedCount
                + " fragment(s) in " + format + " document.";
    }

    /**
     * Creates a ZIP archive from a file or directory.
     *
     * <p>This public utility is retained for backward compatibility. Office conversion
     * itself no longer uses it for the in-memory path.</p>
     *
     * @param sourcePath  the path to a file or directory to archive
     * @param zipFilePath the destination ZIP file path
     * @throws IOException if an error occurs during zipping
     */
    public static void zip(Path sourcePath, Path zipFilePath) throws IOException {
        Path parentDir = zipFilePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        try (OutputStream fos = Files.newOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> paths = Files.walk(sourcePath)) {
                    Iterator<Path> iterator = paths.filter(path -> !Files.isDirectory(path)).iterator();
                    while (iterator.hasNext()) {
                        Path path = iterator.next();
                        Path relativePath = sourcePath.relativize(path);
                        ZipEntry zipEntry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                }
            } else if (Files.isRegularFile(sourcePath)) {
                ZipEntry zipEntry = new ZipEntry(sourcePath.getFileName().toString());
                zos.putNextEntry(zipEntry);
                Files.copy(sourcePath, zos);
                zos.closeEntry();
            } else {
                throw new IllegalArgumentException(
                        "Source path must be a file or a directory: " + sourcePath);
            }
        }
    }

    /**
     * Returns whether font masking should be applied for the given ZIP entry.
     *
     * <p>For XLSX, broad {@code val="..."} masking on worksheet XML is risky because
     * worksheet files contain structural attributes unrelated to fonts. Therefore,
     * XLSX font masking remains limited to {@code xl/sharedStrings.xml}.</p>
     */
    private static boolean shouldMaskFonts(String format, Path relativePath) {
        if (!"xlsx".equals(format)) {
            return true;
        }

        String normalized = relativePath.toString().replace('\\', '/');
        return "xl/sharedStrings.xml".equals(normalized);
    }

    /**
     * Returns a regular expression {@link Pattern} for extracting font declarations
     * in the specified document format.
     *
     * <p>See {@link #FONT_PATTERNS} for the supported formats and attributes.</p>
     *
     * @param format the document format key
     * @return the format-specific font extraction {@link Pattern}, or {@code null} if unsupported
     */
    private static Pattern getFontPattern(String format) {
        return FONT_PATTERNS.get(format);
    }

    /**
     * Converts one XML/XHTML content fragment according to its format and relative path.
     *
     * <p>XLSX worksheet XML is handled narrowly:
     * only inline-string cells are rewritten, and only their {@code <t>} text nodes
     * are converted. Shared strings and other formats continue to use whole-fragment
     * conversion.</p>
     */
    private static String convertXmlContent(
            String format,
            Path relativePath,
            String xml,
            OpenCC converter,
            boolean punctuation
    ) {
        if ("xlsx".equals(format) && isWorksheetPath(relativePath)) {
            return convertXlsxInlineStrings(xml, converter, punctuation);
        }
        return converter.convert(xml, punctuation);
    }

    /**
     * Returns whether the relative path points to an XLSX worksheet XML file.
     */
    private static boolean isWorksheetPath(Path relativePath) {
        String normalized = relativePath.toString().replace('\\', '/');
        return normalized.startsWith("xl/worksheets/") && normalized.endsWith(".xml");
    }

    /**
     * Converts only XLSX inline-string cells in a worksheet XML file.
     */
    private static String convertXlsxInlineStrings(String xml, OpenCC converter, boolean punctuation) {
        Matcher cellMatcher = XLSX_INLINE_STRING_CELL_PATTERN.matcher(xml);
        StringBuffer xmlOut = new StringBuffer();

        while (cellMatcher.find()) {
            String convertedCell = convertXlsxInlineStringCell(
                    cellMatcher.group(),
                    converter,
                    punctuation
            );
            cellMatcher.appendReplacement(xmlOut, Matcher.quoteReplacement(convertedCell));
        }
        cellMatcher.appendTail(xmlOut);

        return xmlOut.toString();
    }

    /**
     * Converts only {@code <t>} text nodes inside one XLSX inline-string cell.
     */
    private static String convertXlsxInlineStringCell(String cellXml, OpenCC converter, boolean punctuation) {
        Matcher textMatcher = XLSX_TEXT_NODE_PATTERN.matcher(cellXml);
        StringBuffer cellOut = new StringBuffer();

        while (textMatcher.find()) {
            String convertedText = converter.convert(textMatcher.group(2), punctuation);
            if (convertedText == null) {
                throw new IllegalStateException("native error: " + converter.getLastError());
            }

            String replacement = textMatcher.group(1) + convertedText + textMatcher.group(3);
            textMatcher.appendReplacement(cellOut, Matcher.quoteReplacement(replacement));
        }
        textMatcher.appendTail(cellOut);

        return cellOut.toString();
    }

}
