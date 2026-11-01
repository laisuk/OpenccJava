package openccjava;
import org.openjdk.jmh.annotations.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class PhraseSliceBenchmark {
    @Param({"baseline", "slice"}) public String matcher;
    @Param({"100", "1000", "10000", "100000", "1000000", "full", "short"}) public String size;
    private OpenCC converter;
    private String input;
    @Setup(Level.Trial) public void setup() throws Exception {
        String file = size.equals("short") ? "SanWenHansSeal.txt" : "sample.txt";
        Path path = Paths.get("openccjava/bench", file);
        if (!Files.exists(path)) path = Paths.get("bench", file);
        input = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (!size.equals("short") && !size.equals("full")) {
            int end = Math.min(Integer.parseInt(size), input.length());
            if (end < input.length() && Character.isHighSurrogate(input.charAt(end - 1))
                    && Character.isLowSurrogate(input.charAt(end))) end--;
            input = input.substring(0, end);
        }
        converter = matcher.equals("slice") ? new ExperimentalSliceOpenCC() : new OpenCC("s2t");
        String actual = converter.s2t(input, false);
        if (!actual.equals(new OpenCC("s2t").s2t(input, false))) throw new AssertionError("Output mismatch");
        System.out.println("matcher=" + matcher + " inputUnits=" + input.length());
    }
    @Benchmark public String conversion() { return converter.s2t(input, false); }
}
