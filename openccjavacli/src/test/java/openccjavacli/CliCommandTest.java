package openccjavacli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void convertMissingConfigReturnsUsage() {
        CommandResult result = execute("convert");
        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode);
        // Picocli default message
        assertTrue(result.stderr.contains("Missing required option"), result.stderr);
        // Option name should appear
        assertTrue(result.stderr.contains("--config"), result.stderr);
        // Keep this
        assertFalse(result.stderr.contains("--list-configs"), result.stderr);
    }

    @Test
    void convertInvalidConfigReturnsUsageAndListsSupportedConfigs() {
        CommandResult result = execute("convert", "-c", "not-a-config");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode);
        assertTrue(result.stderr.contains("Invalid config: not-a-config"), result.stderr);
        assertTrue(result.stderr.contains("Supported configs:"), result.stderr);
        assertTrue(result.stderr.contains("t2s"), result.stderr);
    }

    @Test
    void pdfInvalidConfigReturnsUsage() {
        Path samplePdf = Paths.get("sample.pdf").toAbsolutePath().normalize();

        CommandResult result = execute("pdf", "-i", samplePdf.toString(), "-c", "not-a-config");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode);
        assertTrue(result.stderr.contains("Missing or invalid config: not-a-config"), result.stderr);
        assertTrue(result.stderr.contains("Supported configs:"), result.stderr);
    }

    @Test
    void pdfExtractWritesTextFileFromSamplePdf() throws Exception {
        Path samplePdf = Paths.get("sample.pdf").toAbsolutePath().normalize();
        Path output = tempDir.resolve("sample_extracted.txt");

        CommandResult result = execute("pdf", "-i", samplePdf.toString(), "-e", "-o", output.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode, result.stderr);
        assertTrue(Files.exists(output), "Expected extracted text file to be created");
        assertTrue(Files.size(output) > 0, "Expected extracted text file to be non-empty");
        String text = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        assertFalse(text.trim().isEmpty(), "Expected extracted text content");
    }

    private static CommandResult execute(String... args) {
        try {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            CommandLine commandLine = new CommandLine(new Main())
                    .setOut(new PrintWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8), true))
                    .setErr(new PrintWriter(new OutputStreamWriter(stderr, StandardCharsets.UTF_8), true));

            int exitCode = commandLine.execute(args);
            return new CommandResult(
                    exitCode,
                    stdout.toString(StandardCharsets.UTF_8.name()),
                    stderr.toString(StandardCharsets.UTF_8.name())
            );
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 should always be supported", e);
        }
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
