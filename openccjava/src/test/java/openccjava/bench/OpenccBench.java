package openccjava.bench;

import openccjava.OpenCC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class OpenccBench {

    private static final String SAMPLE_PATH = "bench/sample.txt";
    private static final String CONFIG = "s2t";

    private static final int[] SAMPLE_SIZES = {100, 1_000, 10_000, 100_000, 1_000_000};
    private static final int WARMUP_ROUNDS = 20;
    private static final int PRIME_ROUNDS = 10;
    private static final int RUNS_PER_SAMPLE = 20;

    private OpenccBench() {
    }

    public static void main(String[] args) throws Exception {
        String sample = loadSampleText();

        System.out.println("Loaded sample: " + SAMPLE_PATH);
        System.out.println("Sample chars  : " + sample.length());
        System.out.println("Config        : " + CONFIG);
        System.out.println();

        OpenCC cc = new OpenCC(CONFIG);
        String warmupInput = sliceSample(sample, Math.min(10_000, sample.length()));

        System.out.println("=== Warmup ===");
        warmup(cc, warmupInput, WARMUP_ROUNDS);
        System.out.println("Warmup done: " + WARMUP_ROUNDS + " rounds on " + warmupInput.length() + " chars");
        System.out.println();

        for (BenchCase benchCase : buildBenchCases(sample)) {
            runBenchCase(cc, benchCase);
        }

        System.out.println("=== Explicit cache-prime ===");
        primeUnionCache(cc, warmupInput, PRIME_ROUNDS);
        System.out.println("Cache-prime done: " + PRIME_ROUNDS + " rounds on " + warmupInput.length() + " chars");
        System.out.println();

        BenchCase largestCase = buildLargestBenchCase(sample);
        runBenchCase(new OpenCC(CONFIG), new BenchCase(
                largestCase.label + " (cache-hot)",
                largestCase.input,
                largestCase.runs
        ));
    }

    private static String loadSampleText() throws IOException {
        for (Path candidate : sampleCandidates()) {
            if (Files.exists(candidate)) {
                byte[] bytes = Files.readAllBytes(candidate);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }

        try (InputStream in = OpenccBench.class.getResourceAsStream("/" + SAMPLE_PATH)) {
            if (in != null) {
                return new String(readAllBytes(in), StandardCharsets.UTF_8);
            }
        }

        throw new IOException("Unable to locate sample text at " + SAMPLE_PATH);
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static List<Path> sampleCandidates() {
        List<Path> candidates = new ArrayList<Path>();
        candidates.add(Paths.get(SAMPLE_PATH));
        candidates.add(Paths.get("openccjava").resolve(SAMPLE_PATH));
        return candidates;
    }

    private static List<BenchCase> buildBenchCases(String sample) {
        List<BenchCase> cases = new ArrayList<BenchCase>();
        for (int requestedChars : SAMPLE_SIZES) {
            int actualChars = Math.min(requestedChars, sample.length());
            if (actualChars <= 0) {
                continue;
            }

            String input = sliceSample(sample, actualChars);
            cases.add(new BenchCase(formatChars(actualChars) + " chars", input, RUNS_PER_SAMPLE));

            if (actualChars == sample.length()) {
                break;
            }
        }
        return cases;
    }

    private static BenchCase buildLargestBenchCase(String sample) {
        List<BenchCase> cases = buildBenchCases(sample);
        if (cases.isEmpty()) {
            throw new IllegalStateException("No benchmark cases available");
        }
        return cases.get(cases.size() - 1);
    }

    private static String sliceSample(String sample, int charCount) {
        if (charCount >= sample.length()) {
            return sample;
        }
        return sample.substring(0, charCount);
    }

    private static void warmup(OpenCC cc, String input, int rounds) {
        long checksum = 0L;
        for (int i = 0; i < rounds; i++) {
            String out = cc.convert(input);
            checksum += out.length();
        }
        if (checksum == 0L) {
            throw new IllegalStateException("Unexpected checksum 0");
        }
    }

    private static void primeUnionCache(OpenCC cc, String input, int rounds) {
        long checksum = 0L;
        for (int i = 0; i < rounds; i++) {
            String out = cc.convert(input);
            checksum += out.length();
        }
        if (checksum == 0L) {
            throw new IllegalStateException("Unexpected checksum 0");
        }
    }

    private static void runBenchCase(OpenCC cc, BenchCase benchCase) {
        String input = benchCase.input;
        int runs = benchCase.runs;

        double[] totalMsList = new double[runs];
        double[] mcharsPerSecList = new double[runs];

        long charsPerRun = input.length();
        long totalChecksum = 0L;
        long totalCharsProcessed = charsPerRun * (long) runs;

        System.out.println("=== " + benchCase.label + " ===");
        System.out.println("Chars per run         : " + charsPerRun);
        System.out.println("Run count             : " + runs);
        System.out.println("Total chars processed : " + totalCharsProcessed);

        for (int r = 0; r < runs; r++) {
            long start = System.nanoTime();
            String out = cc.convert(input);
            long end = System.nanoTime();

            long elapsedNs = end - start;
            double elapsedMs = elapsedNs / 1_000_000.0;
            double seconds = elapsedNs / 1_000_000_000.0;
            double charsPerSec = charsPerRun / seconds;
            double mcharsPerSec = charsPerSec / 1_000_000.0;

            totalMsList[r] = elapsedMs;
            mcharsPerSecList[r] = mcharsPerSec;
            totalChecksum += out.length();

            System.out.printf(
                    "Run %2d/%d -> total: %.3f ms | M chars/sec: %.3f%n",
                    (r + 1), runs, elapsedMs, mcharsPerSec
            );

            if (r != runs - 1) {
                System.gc();
            }
        }

        printStats("Total time (ms)", totalMsList);
        printStats("M chars/sec", mcharsPerSecList);

        System.out.println("Checksum sum          : " + totalChecksum);
        System.out.println();
    }

    private static void printStats(String name, double[] values) {
        double min = values[0];
        double max = values[0];
        double sum = 0.0;

        for (double v : values) {
            if (v < min) min = v;
            if (v > max) max = v;
            sum += v;
        }

        double avg = sum / values.length;

        System.out.printf(
                "%-20s min: %.6f | avg: %.6f | max: %.6f%n",
                name,
                min, avg, max
        );
    }

    private static String formatChars(int value) {
        return String.format("%,d", value);
    }

    private static final class BenchCase {
        private final String label;
        private final String input;
        private final int runs;

        private BenchCase(String label, String input, int runs) {
            this.label = label;
            this.input = input;
            this.runs = runs;
        }
    }
}
