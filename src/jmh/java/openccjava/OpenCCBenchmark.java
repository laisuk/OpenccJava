package openccjava;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput) // Measures ops/ms
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class OpenCCBenchmark {

    private OpenCC converter;

    private String text100;
    private String text1000;
    private String text10000;
    private String text100000;

    @Setup(Level.Trial)
    public void setup() {
        converter = new OpenCC("s2t");

        // Base text of ~25 characters
        String base = "这是用于，汉字转换测试，性能表现测试的表视图文本。";

        // Repeat base text to required sizes
        text100 = base.repeat(4);       // ~100 chars
        text1000 = base.repeat(40);     // ~1,000 chars
        text10000 = base.repeat(400);   // ~10,000 chars
        text100000 = base.repeat(4000); // ~100,000 chars
    }

    @Benchmark
    public String convert_100_chars() {
        return converter.convert(text100, false);
    }

    @Benchmark
    public String convert_1k_chars() {
        return converter.convert(text1000, false);
    }

    @Benchmark
    public String convert_10k_chars() {
        return converter.convert(text10000, false);
    }

    @Benchmark
    public String convert_100k_chars() {
        return converter.convert(text100000, false);
    }
}
