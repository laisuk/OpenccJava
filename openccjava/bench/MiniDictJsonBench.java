package openccjava;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Standalone comparison driver; see run-minidict.py for compilation and sampling. */
public final class MiniDictJsonBench {
    private static volatile Object sink;

    private static Object parse(boolean baseline, boolean stream, String json, byte[] bytes) throws Exception {
        if (stream) return baseline
                ? BaselineMiniDictJson.parseToMap(new ByteArrayInputStream(bytes))
                : MiniDictJson.parseToMap(new ByteArrayInputStream(bytes));
        return baseline ? BaselineMiniDictJson.parseToMap(json) : MiniDictJson.parseToMap(json);
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
        String json = new String(bytes, StandardCharsets.UTF_8);
        if (args[1].equals("verify")) {
            Map<String, DictionaryMaxlength.DictEntry> before = BaselineMiniDictJson.parseToMap(json);
            Map<String, DictionaryMaxlength.DictEntry> after = MiniDictJson.parseToMap(json);
            if (!new ArrayList<>(before.keySet()).equals(new ArrayList<>(after.keySet())))
                throw new AssertionError("Dictionary names/order differ");
            int count = 0;
            for (String name : before.keySet()) {
                DictionaryMaxlength.DictEntry a = before.get(name), b = after.get(name);
                if (a.maxLength != b.maxLength || a.minLength != b.minLength
                        || !new ArrayList<>(a.dict.entrySet()).equals(new ArrayList<>(b.dict.entrySet())))
                    throw new AssertionError("Contents/metadata/order differ: " + name);
                count += a.dict.size();
            }
            System.out.println("Equivalent snapshot: " + before.size() + " dictionaries, " + count + " mappings");
            return;
        }
        boolean baseline = args[1].equals("before"), stream = args[2].equals("stream");
        long start = System.nanoTime();
        sink = parse(baseline, stream, json, bytes);
        double cold = (System.nanoTime() - start) / 1e6;
        for (int n = 0; n < 30; n++) sink = parse(baseline, stream, json, bytes);
        // Optional HotSpot allocation counter: cumulative bytes allocated by this thread,
        // including discarded objects, rather than noisy retained-heap differences.
        Object bean = ManagementFactory.getThreadMXBean();
        Method allocated = null;
        try {
            Class<?> type = Class.forName("com.sun.management.ThreadMXBean");
            if ((Boolean) type.getMethod("isThreadAllocatedMemorySupported").invoke(bean)) {
                type.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(bean, true);
                allocated = type.getMethod("getThreadAllocatedBytes", long.class);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // Unsupported JVMs still report timings, with allocation explicitly unavailable.
        }
        double[] times = new double[31];
        long[] allocations = new long[31];
        long thread = Thread.currentThread().getId();
        for (int n = 0; n < times.length; n++) {
            long prior = allocated == null ? 0 : (Long) allocated.invoke(bean, thread);
            start = System.nanoTime();
            sink = parse(baseline, stream, json, bytes);
            times[n] = (System.nanoTime() - start) / 1e6;
            allocations[n] = allocated == null ? -1 : (Long) allocated.invoke(bean, thread) - prior;
        }
        Arrays.sort(times);
        Arrays.sort(allocations);
        System.out.printf(Locale.ROOT, "%.3f %.3f %d%n", cold, times[15], allocations[15]);
    }
}
