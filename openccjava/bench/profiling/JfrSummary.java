import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;

/** JDK 21 analysis utility, independent of the library's Java 8 target. */
public class JfrSummary {
    public static void main(String[] args) throws Exception {
        Map<String, Long> cpu = new HashMap<>(), allocation = new HashMap<>();
        long samples = 0, bytes = 0;
        try (RecordingFile file = new RecordingFile(Paths.get(args[0]))) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String type = event.getEventType().getName();
                boolean execution = type.equals("jdk.ExecutionSample");
                boolean alloc = type.equals("jdk.ObjectAllocationSample");
                if (!execution && !alloc) continue;
                RecordedStackTrace stack = event.getStackTrace();
                if (stack == null || stack.getFrames().isEmpty()) continue;
                // Exclude JMH/framework activity; retain conversion and its callees.
                boolean conversion = stack.getFrames().stream().anyMatch(f ->
                    f.getMethod().getType().getName().equals("openccjava.OpenCC")
                    && (f.getMethod().getName().equals("convertSegmentWithUnion")
                        || f.getMethod().getName().equals("segmentReplaceWithUnion")));
                if (!conversion) continue;
                RecordedFrame leaf = stack.getFrames().get(0);
                String key = leaf.getMethod().getType().getName() + "."
                        + leaf.getMethod().getName() + ":" + leaf.getLineNumber();
                if (execution) {
                    samples++;
                    cpu.merge(key, 1L, Long::sum);
                } else {
                    long weight = event.getLong("weight");
                    bytes += weight;
                    String site = stack.getFrames().stream()
                        .filter(f -> f.getMethod().getType().getName().startsWith("openccjava."))
                        .findFirst().map(f -> f.getMethod().getType().getName() + "."
                            + f.getMethod().getName() + ":" + f.getLineNumber()).orElse("unknown");
                    allocation.merge(event.getClass("objectClass").getName() + " @ " + site,
                            weight, Long::sum);
                }
            }
        }
        System.out.println("Conversion CPU samples=" + samples);
        print(cpu, samples);
        System.out.println("Conversion sampled allocation weight=" + bytes);
        print(allocation, bytes);
    }
    private static void print(Map<String, Long> values, long total) {
        values.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(15).forEach(e -> System.out.printf(Locale.ROOT, "%6.2f%% %12d %s%n",
                100.0 * e.getValue() / total, e.getValue(), e.getKey()));
    }
}
