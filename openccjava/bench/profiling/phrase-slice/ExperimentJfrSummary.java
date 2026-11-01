import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;
public class ExperimentJfrSummary {
    public static void main(String[] args) throws Exception {
        String matcherClass = args.length > 1 && args[1].equals("slice")
                ? "openccjava.ExperimentalSliceOpenCC" : "openccjava.OpenCC";
        Map<String,Long> cpu = new HashMap<>(), alloc = new HashMap<>();
        long samples=0, bytes=0;
        try (RecordingFile file = new RecordingFile(Paths.get(args[0]))) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                boolean execution = event.getEventType().getName().equals("jdk.ExecutionSample");
                boolean allocation = event.getEventType().getName().equals("jdk.ObjectAllocationSample");
                if (!execution && !allocation) continue;
                RecordedStackTrace stack = event.getStackTrace();
                if (stack == null || stack.getFrames().isEmpty()) continue;
                if (!stack.getFrames().stream().anyMatch(f ->
                        f.getMethod().getType().getName().equals(matcherClass)
                        && (f.getMethod().getName().equals("convertSegmentWithUnion")
                            || f.getMethod().getName().equals("segmentReplaceWithUnion")))) continue;
                RecordedFrame leaf = stack.getFrames().get(0);
                String name = leaf.getMethod().getType().getName()+"."+leaf.getMethod().getName();
                if (execution) { samples++; cpu.merge(name,1L,Long::sum); }
                else {
                    long weight=event.getLong("weight"); bytes+=weight;
                    String site=stack.getFrames().stream().filter(f -> f.getMethod().getType().getName().startsWith("openccjava."))
                        .findFirst().map(f -> f.getMethod().getType().getName()+"."+f.getMethod().getName()+":"+f.getLineNumber()).orElse("unknown");
                    alloc.merge(event.getClass("objectClass").getName()+" @ "+site,weight,Long::sum);
                }
            }
        }
        System.out.println("CPU samples="+samples); print(cpu,samples);
        System.out.println("Allocation weight="+bytes); print(alloc,bytes);
    }
    static void print(Map<String,Long> map,long total) {
        map.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(15)
            .forEach(e -> System.out.printf(Locale.ROOT,"%6.2f%% %12d %s%n",100.0*e.getValue()/total,e.getValue(),e.getKey()));
    }
}
