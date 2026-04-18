package openccjavacli;

import openccjava.OpenCC;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.logging.*;

@Command(
        name = "convert",
        description = "\033[1;34mConvert plain text using OpenccJava\033[0m",
        mixinStandardHelpOptions = true
)
public class ConvertCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Option(names = {"-i", "--input"}, paramLabel = "<file>", description = "Input file")
    private File input;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Output file")
    private File output;

    @Option(
            names = {"-c", "--config"},
            paramLabel = "<conversion>",
            required = true,
            completionCandidates = ConfigCandidates.class,
            description = "Conversion configuration. Supported: ${COMPLETION-CANDIDATES}"
    )
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

    @SuppressWarnings("NullableProblems")
    static final class ConfigCandidates implements Iterable<String> {
        @Override
        public java.util.Iterator<String> iterator() {
            return OpenCC.getSupportedConfigs().iterator();
        }
    }

    @Override
    public Integer call() {
        config = normalizeConfig(config);

        if (!OpenCC.isSupportedConfig(config)) {
            printInvalidConfigError(config);
            return ExitCode.USAGE;
        }

        return handleTextConversion();
    }

    private int handleTextConversion() {
        try {
            OpenCC opencc = new OpenCC(config);
            String inputText;

            if (input != null) {
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
                inputText = new String(inputStreamReadAllBytes(), inputCharset);
            }

            String outputText = opencc.convert(inputText, punct);

            if (output != null) {
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
            return ExitCode.OK;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during text conversion", e);
            System.err.println("❌ Exception occurred: " + e.getMessage());
            return ExitCode.SOFTWARE;
        }
    }

    private void printInvalidConfigError(String configValue) {
        PrintWriter err = spec.commandLine().getErr();
        err.println("❌ Invalid config: " + configValue);
        err.println("Supported configs: " + String.join(", ", OpenCC.getSupportedConfigs()));
        spec.commandLine().usage(err);
    }

    private static String normalizeConfig(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
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