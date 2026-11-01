package openccjava;

import org.openjdk.jmh.annotations.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/** Real corpus, warmed direct conversion; disk IO and initialization are excluded. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class S2tCorpusBenchmark {
    @Param({"SanWenHansSeal.txt", "sample.txt"})
    public String corpus;
    @Param({"2147483647"})
    public int maxChars;
    private OpenCC converter;
    private String input;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        if (maxChars <= 0) throw new IllegalArgumentException("maxChars must be positive");
        Path path = Paths.get("openccjava", "bench", corpus);
        if (!Files.exists(path)) path = Paths.get("bench", corpus);
        input = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (input.length() > maxChars) {
            int end = maxChars;
            if (end > 0 && Character.isHighSurrogate(input.charAt(end - 1))
                    && Character.isLowSurrogate(input.charAt(end))) end--;
            input = input.substring(0, end);
        }
        converter = new OpenCC("s2t");
        String direct = converter.s2t(input, false);
        if (!direct.equals(converter.convert(input, false))) {
            throw new IllegalStateException("Direct and dispatched conversion disagree");
        }
        int segments = 0;
        for (int i = 0; i < input.length(); i++) {
            if (DictRefs.isDelimiter(input.charAt(i))) segments++;
        }
        if (!input.isEmpty() && !DictRefs.isDelimiter(input.charAt(input.length() - 1))) segments++;
        System.out.println("Corpus=" + corpus + " UTF16=" + input.length()
                + " codePoints=" + input.codePointCount(0, input.length())
                + " segments=" + segments
                + " outputHash=" + direct.hashCode()
                + " processors=" + Runtime.getRuntime().availableProcessors());
    }

    @Benchmark
    public String directS2t() {
        return converter.s2t(input, false);
    }
}
