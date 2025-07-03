package openccjavacli;

import openccjava.OpenCC;
import openccjava.OfficeDocHelper;
import picocli.CommandLine.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Objects;
import java.util.logging.*;

@Command(name = "convert", description = "Convert text or Office files using OpenCC", mixinStandardHelpOptions = true)
public class ConvertCommand implements Runnable {
    @Option(names = "--list-configs", description = "List all supported OpenCC conversion configurations")
    private boolean listConfigs;

    @Option(names = {"-i", "--input"}, paramLabel = "<file>", description = "Input file")
    private File input;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Output file")
    private File output;

    @Option(names = {"-c", "--config"}, paramLabel = "<conversion>", description = "Conversion configuration")
    private String config;

    @Option(names = {"-p", "--punct"}, description = "Punctuation conversion (default: false)")
    private boolean punct;

    @Option(names = {"--in-enc"}, paramLabel = "<encoding>", defaultValue = "UTF-8", description = "Input encoding")
    private String inEncoding;

    @Option(names = {"--out-enc"}, paramLabel = "<encoding>", defaultValue = "UTF-8", description = "Output encoding")
    private String outEncoding;

    @Option(names = {"--office"}, description = "Enable Office document conversion mode")
    private boolean office;

    @Option(names = {"--format"}, paramLabel = "<format>", description = "Target Office format (e.g., docx, xlsx, pptx, odt, epub)")
    private String format;

    @Option(names = {"--auto-ext"}, description = "Auto-append extension to output file")
    private boolean autoExt;

    @Option(names = {"--keep-font"}, defaultValue = "true", negatable = true, description = "Preserve font-family info (default: true)")
    private boolean keepFont;

    private static final Logger LOGGER = Logger.getLogger(ConvertCommand.class.getName());

    @Override
    public void run() {
        if (listConfigs) {
            System.out.println("Available OpenCC configurations:");
            OpenCC.getSupportedConfigs().forEach(cfg -> System.out.println("  " + cfg));
            return;
        }
        if (config == null || config.isBlank()) {
            System.err.println("❌ Missing required option: --config=<conversion>");
            System.exit(1);
        }
        if (office) {
            handleOfficeConversion();
        } else {
            handleTextConversion();
        }
    }

    private void handleOfficeConversion() {
        if (input == null) {
            System.err.println("❌ Input file is required for Office conversion.");
            System.exit(1);
        }

        String officeFormat = format;
        String inputName = removeExtension(input.getName());
        String ext = getExtension(input.getName());

        // Derive output file if not provided
        if (output == null) {
            String defaultExt = autoExt && format != null ? "." + format : ext;
            String defaultName = inputName + "_converted" + defaultExt;
            output = new File(input.getParentFile(), defaultName);
            System.err.println("ℹ️ Output file not specified. Using: " + output);
        }

        // Infer format if not explicitly given
        if (officeFormat == null) {
            if (ext.isEmpty()) {
                System.err.println("❌ Cannot infer Office format from input file extension.");
                System.exit(1);
            }
            officeFormat = ext.substring(1).toLowerCase();
        }

        // Ensure auto-ext is applied
        if (autoExt && getExtension(output.getName()).isEmpty()) {
            output = new File(output.getAbsolutePath() + "." + officeFormat);
            System.err.println("ℹ️ Auto-extension applied: " + output.getAbsolutePath());
        }

        try {
            OpenCC opencc = new OpenCC(config);
            var result = OfficeDocHelper.convert(input, output, officeFormat, opencc, punct, keepFont);

            if (result.success) {
                System.err.println(result.message + "\n📁 Output saved to: " + output.getAbsolutePath());
            } else {
                System.err.println("❌ Conversion failed: " + result.message);
                System.exit(1);
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error during Office document conversion", ex);
            System.err.println("❌ Exception occurred: " + ex.getMessage());
            System.exit(1);
        }
    }

    private void handleTextConversion() {
        try {
            OpenCC opencc = new OpenCC(config);
            String inputText;

            // Read from file or stdin
            if (input != null) {
                inputText = Files.readString(input.toPath(), java.nio.charset.Charset.forName(inEncoding));
            } else {
                Charset inputCharset = Charset.forName(inEncoding);
                if (System.console() != null) {
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        System.err.println("Notes: If your terminal shows garbage characters, try adding:");
                        System.err.println("       --in-enc=GBK --out-enc=GBK   (Simplified Chinese Windows)");
                        System.err.println("       --in-enc=BIG5 --out-enc=BIG5 (Traditional Chinese Windows)");
                        inputCharset = Objects.equals(inEncoding, "UTF-8")
                                ? Charset.forName("GBK")
                                : inputCharset;
                    }

                    System.err.println("Input Charset: " + inputCharset);
                    System.err.println("Input text to convert, <Ctrl+D> to submit:");
                }

                inputText = new String(System.in.readAllBytes(), inputCharset);
            }

            // Convert
            String outputText = opencc.convert(inputText, punct);

            // Write to file or stdout
            if (output != null) {
                Files.writeString(output.toPath(), outputText, java.nio.charset.Charset.forName(outEncoding));
            } else {
                Charset outputCharset = Charset.forName(outEncoding);
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    outputCharset = Objects.equals(outEncoding, "UTF-8")
                            ? Charset.forName("GBK")
                            : outputCharset;
                }
                System.err.println("Output Charset: " + outputCharset);
                System.out.write(outputText.getBytes(outputCharset));
            }

            String inFrom = (input != null) ? input.getPath() : "<stdin>";
            String outTo = (output != null) ? output.getPath() : "stdout";
            if (System.console() != null) {
                System.err.println("Conversion completed (" + config + "): " + inFrom + " → " + outTo);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during text conversion", e);
            System.err.println("❌ Exception occurred: " + e.getMessage());
            System.exit(1);
        }
    }

    private String removeExtension(String filename) {
        int idx = filename.lastIndexOf(".");
        return (idx != -1) ? filename.substring(0, idx) : filename;
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf(".");
        return (idx != -1) ? filename.substring(idx) : "";
    }
}
