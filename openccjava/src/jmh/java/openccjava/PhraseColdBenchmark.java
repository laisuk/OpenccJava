package openccjava;
import org.openjdk.jmh.annotations.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(5)
@State(Scope.Thread)
public class PhraseColdBenchmark {
    @Param({"baseline", "slice"}) public String matcher;
    private String input;
    @Setup public void setup() throws Exception {
        Path path = Paths.get("openccjava/bench/sample.txt");
        if (!Files.exists(path)) path = Paths.get("bench/sample.txt");
        input = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).substring(0, 10000);
    }
    @Benchmark public String firstConversion() {
        OpenCC cc = matcher.equals("slice") ? new ExperimentalSliceOpenCC() : new OpenCC("s2t");
        return cc.s2t(input, false);
    }
}
