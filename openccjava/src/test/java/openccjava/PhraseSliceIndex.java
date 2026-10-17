package openccjava;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Experimental only: immutable range lookup over existing String mappings. */
final class PhraseSliceIndex {
    final String[] keys;
    final String[] values;
    final int[] hashes;
    private final int mask;

    static boolean supports(Map<String, String> map) {
        // Exact classes only: subclasses can override get() semantics.
        Class<?> type = map.getClass();
        return type == HashMap.class || type == LinkedHashMap.class || type == ConcurrentHashMap.class;
    }

    PhraseSliceIndex(Map<String, String> map) {
        if (!supports(map)) throw new IllegalArgumentException("Unsupported map semantics");
        int capacity = 2;
        while (map.size() > capacity * 0.65) {
            if (capacity >= (1 << 29)) throw new IllegalArgumentException("Experimental index too large");
            capacity <<= 1;
        }
        keys = new String[capacity];
        values = new String[capacity];
        hashes = new int[capacity];
        mask = capacity - 1;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key == null || entry.getValue() == null) continue;
            int hash = key.hashCode();
            int slot = spread(hash) & mask;
            while (keys[slot] != null) slot = (slot + 1) & mask;
            keys[slot] = key;
            values[slot] = entry.getValue();
            hashes[slot] = hash;
        }
    }

    static int hashRange(String input, int start, int length) {
        int hash = 0;
        int end = start + length;
        for (int i = start; i < end; i++) hash = 31 * hash + input.charAt(i);
        return hash;
    }

    private static int spread(int hash) { return hash ^ (hash >>> 16); }

    String lookup(String input, int start, int length, int hash) {
        int slot = spread(hash) & mask;
        String key;
        while ((key = keys[slot]) != null) {
            if (hashes[slot] == hash && key.length() == length
                    && input.regionMatches(start, key, 0, length)) return values[slot];
            slot = (slot + 1) & mask;
        }
        return null;
    }

    long estimatedOwnedBytes() { return 32L + 3 * 16L + 12L * keys.length; }
}
