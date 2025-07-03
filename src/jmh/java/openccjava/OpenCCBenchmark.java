package openccjava;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput) // or Mode.AverageTime
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class OpenCCBenchmark {

    private OpenCC converter;

    @Setup(Level.Trial)
    public void setup() {
        converter = new OpenCC("s2t");
    }

    @Benchmark
    public String convertSimpleText() {
        return converter.convert("汉字转换测试", false);
    }
}
