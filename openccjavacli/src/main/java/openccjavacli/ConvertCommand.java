package openccjavacli;

import openccjava.OpenCC;
import picocli.CommandLine.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.logging.*;

/**
 * Subcommand for converting plain text using OpenCC.
 */
@Command(name = "convert", description = "\033[1;34mConvert plain text using OpenccJava\033[0m", mixinStandardHelpOptions = true)
public class ConvertCommand implements Runnable {
    @Option(names = "--list-configs", description = "List all supported OpenccJava conversion configurations")
    private boolean listConfigs;

    @Option(names = {"-i", "--input"}, paramLabel = "<file>", description = "Input file")
    private File input;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Output file")
    private File output;

    @Option(names = {"-c", "--config"}, paramLabel = "<conversion>", description = "Conversion configuration", required = true)
    private String config;

    @Option(names = {"-p", "--punct"}, description = "Punctuation conversion (default: false)")
    private boolean punct;

    @Option(names = {"--in-enc"}, paramLabel = "<encoding>", defaultValue = "UTF-8", description = "Input encoding")
    private String inEncoding;

    @Option(names = {"--out-enc"}, paramLabel = "<encoding>", defaultValue = "UTF-8", description = "Output encoding")
    private String outEncoding;

    @Option(
            names = "--con-enc",
            paramLabel = "<encoding>",
            description = "Console encoding for interactive mode. Ignored if not attached to a terminal. Common <encoding>: UTF-8, GBK, Big5",
            defaultValue = "UTF-8"
    )
    private String consoleEncoding;

    private static final Logger LOGGER = Logger.getLogger(ConvertCommand.class.getName());
    private static final String BLUE = "\033[1;34m";
    private static final String RESET = "\033[0m";


    @Override
    public void run() {
        if (listConfigs) {
            System.out.println("Available OpenccJava configurations:");
            OpenCC.getSupportedConfigs().forEach(cfg -> System.out.println("  " + cfg));
            return;
        }

        handleTextConversion();
    }

    private void handleTextConversion() {
        try {
            OpenCC opencc = new OpenCC(config);
            String inputText;

            if (input != null) {
                // inputText = Files.readString(input.toPath(), Charset.forName(inEncoding));
                // Java 8: no Files.readString, use readAllBytes
                byte[] bytes = Files.readAllBytes(input.toPath());
                inputText = new String(bytes, Charset.forName(inEncoding));
            } else {
                Charset inputCharset = Charset.forName(normEnc(inEncoding));
                if (System.console() != null) {
                    inputCharset = Charset.forName(normEnc(consoleEncoding));
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        System.err.println("Notes: If your terminal shows garbage characters, try setting:");
                        System.err.println("       --con-enc=GBK (Simplified Chinese Windows)");
                        System.err.println("       --con-enc=BIG5 (Traditional Chinese Windows)");
                    }
                    System.err.println("Input (Charset: " + inputCharset.name() + ")");
                    System.err.println(BLUE + "Input text to convert, <Ctrl+D> (Unix) <Ctrl-Z> (Windows) to submit:" + RESET);
                }
                // inputText = new String(System.in.readAllBytes(), inputCharset);
                // Java 8: no InputStream.readAllBytes, use a helper
                inputText = new String(inputStreamReadAllBytes(), inputCharset);
            }

            String outputText = opencc.convert(inputText, punct);

            if (output != null) {
                // Files.writeString(output.toPath(), outputText, Charset.forName(outEncoding));
                // Java 8: no Files.writeString, use Files.write
                Files.write(output.toPath(), outputText.getBytes(Charset.forName(outEncoding)));
            } else {
                Charset outputCharset = Charset.forName(normEnc(consoleEncoding));
                System.err.println("Output (Charset: " + outputCharset.name() + ")");
                System.out.write(outputText.getBytes(outputCharset));
            }

            String inFrom = (input != null) ? input.getPath() : "<stdin>";
            String outTo = (output != null) ? output.getPath() : "stdout";
            if (System.console() != null) {
                System.err.println(BLUE + "Conversion completed (" + config + "): " + inFrom + " → " + outTo + RESET);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during text conversion", e);
            System.err.println("❌ Exception occurred: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String normEnc(String name) {
        if (name == null) return null;
        String n = name.trim();
        switch (n.toUpperCase(java.util.Locale.ROOT)) {
            case "UTF8":
                return "UTF-8";
            case "CP936":
            case "GB2312":
                return "GBK";
            case "CP950":
                return "Big5";
            default:
                return n;
        }
    }

    private static byte[] inputStreamReadAllBytes() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = System.in.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toByteArray();
    }
}
