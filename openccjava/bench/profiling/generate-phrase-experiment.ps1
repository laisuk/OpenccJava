$ErrorActionPreference = 'Stop'
$source = [IO.File]::ReadAllText((Join-Path $PWD 'openccjava/src/main/java/openccjava/OpenCC.java'))
function Extract-Method([string]$signature) {
    $start = $source.IndexOf($signature)
    if ($start -lt 0) { throw "Missing method: $signature" }
    $body = $source.IndexOf('{', $start)
    $depth = 1
    $end = $body + 1
    while ($depth -gt 0) {
        if ($source[$end] -eq '{') { $depth++ }
        if ($source[$end] -eq '}') { $depth-- }
        $end++
    }
    return $source.Substring($start, $end - $start)
}
$outer = Extract-Method 'public String segmentReplaceWithUnion('
$outer = $outer.Replace('int textLen = text.length();', 'final PhraseSliceIndex[] indexes = indexesFor(part);' + "`n        int textLen = text.length();")
$outer = $outer.Replace('convertSegmentWithUnion(text, part)', 'convertSegmentWithUnion(text, part, indexes)').Replace('convertSegmentWithUnion(seg, part)', 'convertSegmentWithUnion(seg, part, indexes)').Replace('convertSegmentWithUnion(segment, part)', 'convertSegmentWithUnion(segment, part, indexes)')
$inner = Extract-Method 'private static String convertSegmentWithUnion('
$inner = $inner.Replace('DictRefs.DictPartition part', 'DictRefs.DictPartition part, PhraseSliceIndex[] indexes')
$inner = $inner.Replace('final String sub = input.substring(i, j);', 'final int hash = PhraseSliceIndex.hashRange(input, i, len);' + "`n                                String sub = null;")
$inner = $inner.Replace('final String repl = e.dict.get(sub);', @'
final PhraseSliceIndex index = indexes[dictIndex];
                                    final String repl;
                                    if (index != null) repl = index.lookup(input, i, len, hash);
                                    else {
                                        if (sub == null) sub = input.substring(i, j);
                                        repl = e.dict.get(sub);
                                    }
'@)
if (!$inner.Contains('index.lookup(input, i, len, hash)') -or $inner.Contains('final String sub = input.substring(i, j);')) {
    throw 'Matcher shape changed; inspect the experimental substitutions before proceeding.'
}
$split = Extract-Method 'private static List<int[]> getSplitRanges('
$parallel = Extract-Method 'private static boolean shouldRunSegmentReplaceInParallel('
$header = @'
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
'@
$result = $header + "`n" + $outer + "`n" + $inner + "`n" + $split + "`n" + $parallel + "`n}`n"
[IO.File]::WriteAllText((Join-Path $PWD 'openccjava/src/test/java/openccjava/ExperimentalSliceOpenCC.java'), $result)

# Preserve the existing CI-style runner's methodology in an experimental copy.
$ci = [IO.File]::ReadAllText((Join-Path $PWD 'openccjava/src/test/java/openccjava/bench/OpenccBench.java'))
$ci = $ci.Replace('package openccjava.bench;', 'package openccjava;').Replace('OpenccBench', 'PhraseSliceCiBench')
$ci = $ci.Replace('public static void main(String[] args) throws Exception {', 'private static String matcher;' + "`n    private static OpenCC newConverter() { return matcher.equals(`"slice`") ? new ExperimentalSliceOpenCC() : new OpenCC(CONFIG); }`n    public static void main(String[] args) throws Exception {`n        matcher = args[0];")
# Replace the original construction sites, leaving the factory itself intact.
$ci = $ci.Replace('OpenCC cc = new OpenCC(CONFIG);', 'OpenCC cc = newConverter();').Replace('runBenchCase(new OpenCC(CONFIG),', 'runBenchCase(newConverter(),')
[IO.File]::WriteAllText((Join-Path $PWD 'openccjava/src/test/java/openccjava/PhraseSliceCiBench.java'), $ci)
