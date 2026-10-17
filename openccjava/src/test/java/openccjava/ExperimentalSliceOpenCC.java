package openccjava;
import java.util.*;
import java.util.stream.IntStream;
import openccjava.DictionaryMaxlength.DictEntry;
import static openccjava.DictRefs.isDelimiter;

/** GENERATED benchmark/test-only copy of the current matcher. Production is untouched. */
public final class ExperimentalSliceOpenCC extends OpenCC {
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    private static final int TEXT_THRESHOLD = 10_100;
    final Map<DictEntry, PhraseSliceIndex> dictionaryIndexes = new IdentityHashMap<>();
    private final Map<DictRefs.DictPartition, PhraseSliceIndex[]> partitions = new IdentityHashMap<>();
    public ExperimentalSliceOpenCC() { super("s2t"); }
    public ExperimentalSliceOpenCC(OpenccConfig config, List<CustomDictSpec> custom) { super(config, custom); }
    synchronized PhraseSliceIndex[] indexesFor(DictRefs.DictPartition part) {
        PhraseSliceIndex[] indexes = partitions.get(part);
        if (indexes == null) {
            indexes = new PhraseSliceIndex[part.phraseDicts.size()];
            for (int i = 0; i < indexes.length; i++) {
                DictEntry entry = part.phraseDicts.get(i);
                if (!PhraseSliceIndex.supports(entry.dict)) continue;
                PhraseSliceIndex index = dictionaryIndexes.get(entry);
                if (index == null) {
                    index = new PhraseSliceIndex(entry.dict);
                    dictionaryIndexes.put(entry, index);
                }
                indexes[i] = index;
            }
            partitions.put(part, indexes);
        }
        return indexes;
    }
    void prepare(DictRefs plan) {
        plan.applySegmentReplace("", (text, part) -> { indexesFor(part); return text; });
    }
    public long estimatedIndexBytes() {
        long bytes = 0;
        for (PhraseSliceIndex index : dictionaryIndexes.values()) bytes += index.estimatedOwnedBytes();
        return bytes;
    }
public String segmentReplaceWithUnion(String text, DictRefs.DictPartition part) {
        if (text == null || text.isEmpty()) return text;
        if (part.phraseDicts.isEmpty() && part.singleDicts.isEmpty()) return text;

        final PhraseSliceIndex[] indexes = indexesFor(part);
        int textLen = text.length();
        List<int[]> ranges = getSplitRanges(text, true);
        int numSegments = ranges.size();

        if (numSegments == 1 &&
                ranges.get(0)[0] == 0 &&
                ranges.get(0)[1] == textLen) {
            return convertSegmentWithUnion(text, part, indexes);
        }

//        boolean useParallel = textLen > 100_000 || numSegments > 1_000;
        boolean useParallel = shouldRunSegmentReplaceInParallel(
                textLen,
                numSegments,
                1_000
        );
        int sbCapacity = textLen + (textLen >> 4);
        StringBuilder sb = new StringBuilder(sbCapacity);

        if (useParallel) {
            String[] segments = new String[numSegments];

            IntStream.range(0, numSegments).parallel().forEach(i -> {
                int[] range = ranges.get(i);
                String seg = text.substring(range[0], range[1]);
                segments[i] = convertSegmentWithUnion(seg, part, indexes);
            });

            for (String seg : segments) sb.append(seg);
        } else {
            for (int[] range : ranges) {
                String segment = text.substring(range[0], range[1]);
                sb.append(convertSegmentWithUnion(segment, part, indexes));
            }
        }
        return sb.toString();
    }
private static String convertSegmentWithUnion(
            String input,
            DictRefs.DictPartition part, PhraseSliceIndex[] indexes
    ) {
        final int n = input.length();
        if (n == 0) return input;

        final StringBuilder out = new StringBuilder(n + (n >> 4));
        final StarterUnion union = part.union;
        final int phraseDictCount = part.phraseDicts.size();
        final boolean hasPhrases = phraseDictCount != 0;
        final boolean hasSingles = part.singleIndex != null;

        int i = 0;
        while (i < n) {
            final int cp = input.codePointAt(i);
            final int starterLen = Character.charCount(cp);

            if (union != null && !union.hasStarter(cp)) {
                out.appendCodePoint(cp);
                i += starterLen;
                continue;
            }

            String hit = null;
            int hitLen = 0;

            if (hasPhrases) {
                final int remaining = n - i;
                if (remaining >= part.phraseMinLen) {
                    int tryMax = Math.min(Math.min(part.phraseMaxLen, part.roundMaxLen), remaining);
                    // Ordinary short-key probing retains its existing mask-only fast path.
                    if (union != null && tryMax >= 64) tryMax = Math.min(tryMax, union.maxLen(cp));
                    final int tryMin = part.phraseMinLen;
                    if (tryMax >= tryMin) {
                        final long lMask = (union != null) ? union.lenMask(cp) : ~0L;
                        if (lMask != 0L || tryMax >= 64) {
                            outer:
                            for (int len = tryMax; len >= tryMin; len--) {
                                if (len < 64 && ((lMask >>> len) & 1L) == 0L) continue;

                                final int j = i + len;
                                if (j > n) continue;

                                final int hash = PhraseSliceIndex.hashRange(input, i, len);
                                String sub = null;
                                // Partitions contain immutable ArrayLists; indexed access
                                // preserves precedence without allocating an iterator.
                                for (int dictIndex = 0; dictIndex < phraseDictCount; dictIndex++) {
                                    final DictEntry e = part.phraseDicts.get(dictIndex);
                                    if (len < e.minLength || len > e.maxLength) continue;
                                    final PhraseSliceIndex index = indexes[dictIndex];
                                    final String repl;
                                    if (index != null) repl = index.lookup(input, i, len, hash);
                                    else {
                                        if (sub == null) sub = input.substring(i, j);
                                        repl = e.dict.get(sub);
                                    }
                                    if (repl != null) {
                                        hit = repl;
                                        hitLen = len;
                                        break outer;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (hit == null && hasSingles) {
                hit = part.singleIndex.get(cp);
                if (hit != null) hitLen = starterLen;
            }

            if (hit != null) {
                out.append(hit);
                i += hitLen;
            } else {
                out.appendCodePoint(cp);
                i += starterLen;
            }
        }

        return out.toString();
    }
private static List<int[]> getSplitRanges(String text, boolean inclusive) {
        List<int[]> result = new ArrayList<>();
        int start = 0;
        final int textLength = text.length(); // Cache text length

        for (int i = 0; i < textLength; i++) {
            if (isDelimiter(text.charAt(i))) {
                if (inclusive) {
                    // Optimized: Directly add the inclusive range
                    result.add(new int[]{start, i + 1});
                } else {
                    // Optimized: Avoid adding empty ranges if i == start
                    if (i > start) {
                        result.add(new int[]{start, i});     // before delimiter
                    }
                    result.add(new int[]{i, i + 1});         // delimiter itself
                }
                start = i + 1; // Update start for the next segment
            }
        }

        // Add the last segment if it exists
        if (start < textLength) {
            result.add(new int[]{start, textLength});
        }

        return result;
    }
private static boolean shouldRunSegmentReplaceInParallel(
            int textLength,
            int segmentCount,
            int segmentThreshold
    ) {
        return AVAILABLE_PROCESSORS > 1
                && (textLength > TEXT_THRESHOLD
                || segmentCount > segmentThreshold);
    }
}
