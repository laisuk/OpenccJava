package openccjava;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Represents a container for all OpenCC dictionary mappings,
 * including phrase-level and character-level dictionaries.
 *
 * <p>This class supports loading from:
 * <ul>
 *     <li>JSON-serialized form (used in deployments)</li>
 *     <li>Individual dictionary text files (used during development or as fallback)</li>
 * </ul>
 *
 * <p>Each dictionary is stored as a {@link DictEntry} with:
 * <ul>
 *     <li>{@code dict}: key-value pairs of source→target</li>
 *     <li>{@code maxLength}: the longest phrase/key length</li>
 *     <li>{@code minLength}: the shortest phrase/key length</li>
 * </ul>
 * Holds multiple dictionary entries, each with a defined maximum key length.
 * Used for efficient longest-match text conversion.
 */
public class DictionaryMaxlength {
    /**
     * Constructs an empty {@code DictionaryMaxlength} instance.
     * All dictionary entries must be assigned manually.
     */
    public DictionaryMaxlength() {
        // No-op constructor for deserialization or manual setup
    }

    /**
     * Represents a dictionary entry with mapping data and max phrase length.
     */
//    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static class DictEntry {
        /**
         * Key-value dictionary (e.g., "电脑" → "計算機")
         */
        public Map<String, String> dict;

        /**
         * Maximum phrase length in this dictionary
         */
        public int maxLength;

        /**
         * Minimum phrase length in this dictionary
         */
        public int minLength; // NEW

        /**
         * Constructs an empty dictionary entry.
         * <p>Kept for potential future serialization frameworks or manual instantiation.</p>
         */
        public DictEntry() {
            this.dict = new HashMap<>();
            this.maxLength = 0;
            this.minLength = 0;
        }

        /**
         * Constructs a new dictionary entry.
         *
         * @param dict      The dictionary mapping strings.
         * @param maxLength The maximum key length in the dictionary.
         * @param minLength The minimum key length in the dictionary.
         */
        public DictEntry(Map<String, String> dict, int maxLength, int minLength) {
            this.dict = Objects.requireNonNull(dict, "dict");
            this.maxLength = maxLength;
            this.minLength = minLength;
        }
    }

    // Dictionary fields (populated via JSON or fallback loading)
    // Simplified to Traditional
    /**
     * Simplified character mappings.
     */
    public DictEntry st_characters;
    /**
     * Simplified phrase mappings.
     */
    public DictEntry st_phrases;
    /**
     * Simplified punctuations.
     */
    public DictEntry st_punctuations;

    // Traditional to Simplified
    /**
     * Traditional character mappings.
     */
    public DictEntry ts_characters;
    /**
     * Traditional phrase mappings.
     */
    public DictEntry ts_phrases;
    /**
     * Traditional punctuations.
     */
    public DictEntry ts_punctuations;

    // Traditional to Taiwan
    /**
     * Taiwan phrase mappings.
     */
    public DictEntry tw_phrases;
    /**
     * Taiwan phrase reverse mappings.
     */
    public DictEntry tw_phrases_rev;
    /**
     * Taiwan variant mappings.
     */
    public DictEntry tw_variants;
    /**
     * Taiwan variant reverse mappings.
     */
    public DictEntry tw_variants_rev;
    /**
     * Taiwan variant reverse phrase mappings.
     */
    public DictEntry tw_variants_rev_phrases;

    // Hong Kong variants
    /**
     * Hong Kong variant mappings.
     */
    public DictEntry hk_variants;
    /**
     * Hong Kong variant reverse mappings.
     */
    public DictEntry hk_variants_rev;
    /**
     * Hong Kong variant reverse phrase mappings.
     */
    public DictEntry hk_variants_rev_phrases;

    // Japanese Shinjitai
    /**
     * Japanese Shinjitai character mappings.
     */
    public DictEntry jps_characters;
    /**
     * Japanese Shinjitai phrase mappings.
     */
    public DictEntry jps_phrases;
    /**
     * Japanese variant mappings.
     */
    public DictEntry jp_variants;
    /**
     * Japanese variant reverse mappings.
     */
    public DictEntry jp_variants_rev;

    /**
     * Returns a human-readable summary showing how many dictionaries are loaded.
     */
    @Override
    public String toString() {
        long count = Arrays.stream(this.getClass().getFields())
                .filter(f -> {
                    try {
                        DictEntry entry = (DictEntry) f.get(this);
                        return entry != null && entry.dict != null && !entry.dict.isEmpty();
                    } catch (IllegalAccessException e) {
                        return false;
                    }
                })
                .count();
        return "<DictionaryMaxlength with " + count + " loaded dicts>";
    }

    /**
     * Loads all dictionary files from the default "dicts" directory.
     *
     * @return the fully populated {@code DictionaryMaxlength} object
     */
    public static DictionaryMaxlength fromDicts() {
        return fromDicts("dicts");
    }

    /**
     * Loads all dictionary files from the specified base directory.
     *
     * @param basePath the path to the directory containing dictionary .txt files
     * @return the fully populated {@code DictionaryMaxlength} object
     */
    public static DictionaryMaxlength fromDicts(String basePath) {
        final DictionaryMaxlength r = new DictionaryMaxlength();

        // ----- filenames (ordered, then wrapped unmodifiable) -----
        final Map<String, String> files = getFiles();

        // ----- assignment table (ordered, then wrapped unmodifiable) -----
        final Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> assign = getAssign();

        for (Map.Entry<String, String> kv : files.entrySet()) {
            final String dictName = kv.getKey();
            final String filename = kv.getValue();

            final Path fsPath = Paths.get(basePath, filename);
            try {
                final DictEntry entry;
                if (Files.exists(fsPath)) {
                    entry = loadDictionaryMaxlength(fsPath);
                } else {
                    final String resPath = "/" + basePath + "/" + filename;
                    try (InputStream in = DictionaryMaxlength.class.getResourceAsStream(resPath)) {
                        if (in == null) throw new FileNotFoundException(filename);
                        entry = loadDictionaryMaxlength(in);
                    }
                }

                final BiConsumer<DictionaryMaxlength, DictEntry> setter = assign.get(dictName);
                if (setter == null) {
                    throw new IllegalStateException("No assign mapping for dict: " + dictName);
                }
                setter.accept(r, entry);

            } catch (IOException ex) {
                throw new RuntimeException("Error loading dict: " + dictName + " (" + filename + ")", ex);
            }
        }

        return r;
    }

    /**
     * Returns the mapping of dictionary identifiers to field setters.
     * <p>
     * Each entry links a dictionary name (e.g. {@code "st_characters"})
     * to a {@link BiConsumer} that assigns a loaded {@link DictEntry}
     * into the correct {@link DictionaryMaxlength} field.
     * </p>
     * <p>
     * The map preserves insertion order and is unmodifiable.
     * </p>
     *
     * @return an unmodifiable assignment map
     */
    private static Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> getAssign() {
        return ASSIGN;
    }

    // --- No-reflection field assignment table (Java 8 compatible) ---
    private static final Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> ASSIGN;

    static {
        Map<String, BiConsumer<DictionaryMaxlength, DictEntry>> m = new LinkedHashMap<>();

        m.put("st_characters", (o, e) -> o.st_characters = e);
        m.put("st_phrases", (o, e) -> o.st_phrases = e);
        m.put("st_punctuations", (o, e) -> o.st_punctuations = e);
        m.put("ts_characters", (o, e) -> o.ts_characters = e);
        m.put("ts_phrases", (o, e) -> o.ts_phrases = e);
        m.put("ts_punctuations", (o, e) -> o.ts_punctuations = e);
        m.put("tw_phrases", (o, e) -> o.tw_phrases = e);
        m.put("tw_phrases_rev", (o, e) -> o.tw_phrases_rev = e);
        m.put("tw_variants", (o, e) -> o.tw_variants = e);
        m.put("tw_variants_rev", (o, e) -> o.tw_variants_rev = e);
        m.put("tw_variants_rev_phrases", (o, e) -> o.tw_variants_rev_phrases = e);
        m.put("hk_variants", (o, e) -> o.hk_variants = e);
        m.put("hk_variants_rev", (o, e) -> o.hk_variants_rev = e);
        m.put("hk_variants_rev_phrases", (o, e) -> o.hk_variants_rev_phrases = e);
        m.put("jps_characters", (o, e) -> o.jps_characters = e);
        m.put("jps_phrases", (o, e) -> o.jps_phrases = e);
        m.put("jp_variants", (o, e) -> o.jp_variants = e);
        m.put("jp_variants_rev", (o, e) -> o.jp_variants_rev = e);

        ASSIGN = Collections.unmodifiableMap(m);
    }

    /**
     * Returns the filename mapping for dictionary resources.
     * <p>
     * Each entry in the map associates a string key (e.g., {@code "st_characters"})
     * with the corresponding dictionary file name (e.g., {@code "STCharacters.txt"}).
     * This mapping is used by {@link #fromDicts(String)} to locate the dictionary
     * files either on the filesystem or on the classpath.
     * </p>
     * <p>
     * The returned map preserves insertion order and is unmodifiable.
     * Attempting to modify it will result in an {@link UnsupportedOperationException}.
     * </p>
     *
     * @return an unmodifiable map of dictionary identifiers to file names
     */
    private static Map<String, String> getFiles() {
        final Map<String, String> files;
        {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("st_characters", "STCharacters.txt");
            m.put("st_phrases", "STPhrases.txt");
            m.put("st_punctuations", "STPunctuations.txt");
            m.put("ts_characters", "TSCharacters.txt");
            m.put("ts_phrases", "TSPhrases.txt");
            m.put("ts_punctuations", "TSPunctuations.txt");
            m.put("tw_phrases", "TWPhrases.txt");
            m.put("tw_phrases_rev", "TWPhrasesRev.txt");
            m.put("tw_variants", "TWVariants.txt");
            m.put("tw_variants_rev", "TWVariantsRev.txt");
            m.put("tw_variants_rev_phrases", "TWVariantsRevPhrases.txt");
            m.put("hk_variants", "HKVariants.txt");
            m.put("hk_variants_rev", "HKVariantsRev.txt");
            m.put("hk_variants_rev_phrases", "HKVariantsRevPhrases.txt");
            m.put("jps_characters", "JPShinjitaiCharacters.txt");
            m.put("jps_phrases", "JPShinjitaiPhrases.txt");
            m.put("jp_variants", "JPVariants.txt");
            m.put("jp_variants_rev", "JPVariantsRev.txt");
            files = Collections.unmodifiableMap(m);
        }
        return files;
    }

    /**
     * Parses the content of a dictionary text file into a {@code DictEntry}.
     *
     * <p>Expected format per line:
     * <ul>
     *   <li>Source phrase, followed by a TAB character (<code>'\t'</code>)</li>
     *   <li>Translation text, which may contain additional tokens after a space or tab;
     *       only the first token is used as the translation value</li>
     *   <li>Lines starting with <code>#</code> or <code>//</code>, or blank lines, are ignored</li>
     * </ul>
     *
     * <p>The maximum key length is computed using UTF-16 {@link String#length()}, so surrogate pairs
     * count as 2 code units. Surrogate handling is deferred to the conversion logic.</p>
     *
     * @param br a {@link BufferedReader} providing UTF-8 text from the dictionary file
     * @return a {@code DictEntry} containing the parsed key-value pairs and max phrase length
     * @throws IOException if an I/O error occurs while reading
     */
    private static DictEntry loadDictionaryMaxlength(BufferedReader br) throws IOException {
        Map<String, String> dict = new HashMap<>();
        int maxLength = 1;
        int minLength = Integer.MAX_VALUE;
        int lineNo = 0;

        for (String raw; (raw = br.readLine()) != null; ) {
            lineNo++;
//            String line = raw.strip();
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            int tab = line.indexOf('\t');
            if (tab < 0) {
                System.err.println("Warning: malformed (no TAB) at line " + lineNo + ": " + raw);
                continue;
            }

            String key = line.substring(0, tab);
            if (lineNo == 1 && !key.isEmpty() && key.charAt(0) == '\uFEFF') key = key.substring(1);

            // first token after TAB (space OR tab ends it)
            String val = getRestString(line, tab);

            if (key.isEmpty() || val.isEmpty()) {
                System.err.println("Warning: empty key/value at line " + lineNo + ": " + raw);
                continue;
            }

            dict.put(key, val);
            // Use UTF-16 length (consistent with your existing maxLength logic).
            // Non-BMP code points count as 2 (same as before).
            int len = key.length();
            if (len > maxLength) maxLength = len;
            if (len < minLength) minLength = len;
        }
        if (dict.isEmpty()) {
            // No entries: keep old behavior; maxLength=1, minLength=1 (or 0 if you prefer).
            return new DictEntry(dict, 1, 1);
        } else {
            if (minLength == Integer.MAX_VALUE) minLength = 1; // defensive, shouldn't happen
            return new DictEntry(dict, maxLength, minLength);
        }
    }

    private static String getRestString(String line, int tab) {
        String rest = line.substring(tab + 1);
        // Java 8 replacement for stripLeading()
        int idx = 0;
        while (idx < rest.length() && (rest.charAt(idx) == ' ' || rest.charAt(idx) == '\t')) {
            idx++;
        }
        rest = rest.substring(idx);

        int end = -1;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == ' ' || c == '\t') {
                end = i;
                break;
            }
        }
        return (end >= 0) ? rest.substring(0, end) : rest;
    }

    /**
     * Loads a dictionary file from the filesystem into a {@code DictEntry}.
     *
     * <p>This method opens the file as UTF-8 and parses it line by line using
     * {@link #loadDictionaryMaxlength(BufferedReader)}.</p>
     *
     * @param file the path to the dictionary text file
     * @return a {@code DictEntry} containing the parsed key-value pairs and max phrase length
     * @throws IOException if the file cannot be opened or read
     */
    private static DictEntry loadDictionaryMaxlength(Path file) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return loadDictionaryMaxlength(br);
        }
    }

    /**
     * Loads a dictionary file from an input stream into a {@code DictEntry}.
     *
     * <p>This method wraps the stream in a UTF-8 {@link BufferedReader} and parses it line by line
     * using {@link #loadDictionaryMaxlength(BufferedReader)}.</p>
     *
     * @param in an {@link InputStream} containing the UTF-8 dictionary text
     * @return a {@code DictEntry} containing the parsed key-value pairs and max phrase length
     * @throws IOException if an I/O error occurs while reading
     */
    private static DictEntry loadDictionaryMaxlength(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return loadDictionaryMaxlength(br);
        }
    }

    // --- Zero-dependency JSON loading ---

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON file without using external libraries.
     * <p>
     * This method expects the JSON to follow the {@code dictionary_maxlength.json} schema:
     * each top-level field maps to an array {@code [ { "phrase": "translation", ... }, maxLength ]}.
     * </p>
     *
     * @param path the path to the JSON file (UTF-8 encoded)
     * @return a populated {@code DictionaryMaxlength} instance
     * @throws IOException              if reading the file fails
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    public static DictionaryMaxlength fromJsonNoDeps(Path path) throws IOException {
        Map<String, DictEntry> all = MiniDictJson.parseToMap(path);
        return hydrate(all);
    }

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON input stream without using external libraries.
     * <p>
     * This method expects the JSON to follow the {@code dictionary_maxlength.json} schema:
     * each top-level field maps to an array {@code [ { "phrase": "translation", ... }, maxLength ]}.
     * </p>
     *
     * @param in an input stream containing the JSON (UTF-8 encoded)
     * @return a populated {@code DictionaryMaxlength} instance
     * @throws IOException              if reading from the stream fails
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    public static DictionaryMaxlength fromJsonNoDeps(InputStream in) throws IOException {
        Map<String, DictEntry> all = MiniDictJson.parseToMap(in);
        return hydrate(all);
    }

    /**
     * Loads a {@code DictionaryMaxlength} from a JSON text string (no external deps).
     *
     * @param jsonString complete JSON document text
     * @return populated {@code DictionaryMaxlength}
     * @throws IllegalArgumentException if the JSON is malformed or violates the expected schema
     */
    public static DictionaryMaxlength fromJsonStringNoDeps(String jsonString) {
        Map<String, DictEntry> all = MiniDictJson.parseToMap(jsonString);
        return hydrate(all);
    }

    /**
     * Convenience: loads from a filesystem path string (delegates to the {@link java.nio.file.Path} overload).
     *
     * @param path JSON file path (UTF-8)
     * @return populated {@code DictionaryMaxlength}
     * @throws IOException if reading fails
     */
    public static DictionaryMaxlength fromJsonFileNoDeps(String path) throws IOException {
        return fromJsonNoDeps(java.nio.file.Paths.get(path));
    }

    /**
     * Populates a new {@link DictionaryMaxlength} instance from a parsed
     * {@code Map<String, DictEntry>} as returned by {@link MiniDictJson}.
     *
     * <p>This method iterates over all top-level entries in the parsed map and,
     * if the key matches a known dictionary field (as defined in {@code ASSIGN}),
     * invokes the corresponding setter to store the {@link DictEntry} into
     * the new instance.</p>
     *
     * <p>Unknown keys are ignored, but a warning is printed to
     * {@code System.err} for diagnostic purposes.</p>
     *
     * @param all a map of field names to {@link DictEntry} objects,
     *            typically produced by {@link MiniDictJson#parseToMap}
     * @return a fully populated {@link DictionaryMaxlength} instance
     */
    private static DictionaryMaxlength hydrate(Map<String, DictEntry> all) {
        DictionaryMaxlength r = new DictionaryMaxlength();
        for (Map.Entry<String, DictEntry> kv : all.entrySet()) {
            BiConsumer<DictionaryMaxlength, DictEntry> setter = ASSIGN.get(kv.getKey());
            if (setter != null) {
                setter.accept(r, kv.getValue());
            } else {
                // Unknown key: ignore or log; your call
                System.err.println("Unknown dict key in JSON: " + kv.getKey());
            }
        }
        return r;
    }

    // --- Public no-deps serializer ---------------------------------------------

    /**
     * Serializes this {@code DictionaryMaxlength} to JSON without external libraries.
     * <p>Format per field: {@code "name": [ { "k":"v", ... }, maxLength ]}</p>
     *
     * @param outputPath path to write (UTF-8)
     * @throws IOException if writing fails
     */
    public void serializeToJsonNoDeps(String outputPath) throws IOException {
        try (Writer w = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(Paths.get(outputPath)), StandardCharsets.UTF_8))) {
            writeJsonNoDeps(w, /*pretty*/ true, /*sortKeys*/ true);
        }
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to JSON without external libraries.
     * <p>Format per field: {@code "name": [ { "k":"v", ... }, maxLength ]}</p>
     *
     * @param outputPath the target file path where the JSON will be written (UTF-8)
     * @throws java.io.IOException if writing fails
     */
    public void serializeToJsonNoDeps(Path outputPath) throws IOException {
        try (Writer w = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writeJsonNoDeps(w, /*pretty*/ true, /*sortKeys*/ true);
        }
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to a JSON string without external libraries.
     * <p>Format per field: {@code "name": [ { "k":"v", ... }, maxLength ]}</p>
     *
     * @return pretty-printed JSON (UTF-16 in-memory; write with UTF-8 when saving to disk)
     * @throws IOException if an I/O error occurs while generating the JSON
     */
    public String serializeToJsonStringNoDeps() throws IOException {
        StringWriter sw = new StringWriter(1 << 20); // pre-size to ~1MB to reduce reallocates
        writeJsonNoDeps(sw, /*pretty*/ false, /*sortKeys*/ false);
        return sw.toString();
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to a compact JSON string without external libraries.
     *
     * @return the serialized JSON string in compact form
     * @throws java.io.IOException if serialization fails
     */
    public String serializeToJsonStringNoDepsCompact() throws IOException {
        StringWriter sw = new StringWriter(1 << 20);
        writeJsonNoDeps(sw, /*pretty*/ false, /*sortKeys*/ false);
        return sw.toString();
    }

    /**
     * Serializes this {@code DictionaryMaxlength} to a compact JSON file (UTF-8), no external libraries.
     * <p>
     * Compact form = no indentation/newlines, natural key order (sortKeys=false).
     *
     * @param outputPath destination path (will be created or truncated)
     * @throws IOException if writing fails
     */
    public void serializeToJsonFileNoDepsCompact(Path outputPath) throws IOException {
        // Use a buffered UTF-8 writer for speed and correctness.
        try (Writer w = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8),
                1 << 20)) {
            writeJsonNoDeps(w, /* pretty */ false, /* sortKeys */ false);
            w.flush();
        }
    }

    // --- Implementation ----------------------------------------------------------

    /**
     * Convenience overload of {@link #writeJsonNoDeps(Writer, boolean, boolean)}
     * that defaults to sorting keys in deterministic (UTF-16 natural) order.
     *
     * @param w      the {@link Writer} to receive the JSON output (UTF-8 recommended)
     * @param pretty if {@code true}, output will be pretty-printed with newlines
     *               and indentation; if {@code false}, output will be compact
     * @throws IOException if writing to {@code w} fails
     */
    private void writeJsonNoDeps(Writer w, boolean pretty) throws IOException {
        writeJsonNoDeps(w, pretty, /*sortKeys*/ true); // or false for original order
    }

    /**
     * Writes this {@link DictionaryMaxlength} instance as JSON without using
     * any external libraries.
     *
     * <p>The output format is schema-specific to OpenCC dictionaries:
     * each field is written as {@code "name": [ { "key": "value", ... }, maxLength ]}
     * with optional pretty-printing and deterministic key ordering.</p>
     *
     * @param w        the {@link Writer} to receive the JSON output (UTF-8 recommended)
     * @param pretty   if {@code true}, output will be pretty-printed with newlines
     *                 and indentation; if {@code false}, output will be compact
     * @param sortKeys if {@code true}, dictionary keys will be written in sorted
     *                 (UTF-16 natural) order for deterministic output; if {@code false},
     *                 keys will be written in their map iteration order
     * @throws IOException if writing to {@code w} fails
     */
    private void writeJsonNoDeps(Writer w, boolean pretty, boolean sortKeys) throws IOException {
        final String nl = pretty ? "\n" : "";
        final String ind1 = pretty ? "  " : "";
        final String ind2 = pretty ? "    " : "";
        boolean firstField = true;

        Object[][] fields = {
                {"st_characters", st_characters},
                {"st_phrases", st_phrases},
                {"st_punctuations", st_punctuations},   // NEW
                {"ts_characters", ts_characters},
                {"ts_phrases", ts_phrases},
                {"ts_punctuations", ts_punctuations},   // NEW
                {"tw_phrases", tw_phrases},
                {"tw_phrases_rev", tw_phrases_rev},
                {"tw_variants", tw_variants},
                {"tw_variants_rev", tw_variants_rev},
                {"tw_variants_rev_phrases", tw_variants_rev_phrases},
                {"hk_variants", hk_variants},
                {"hk_variants_rev", hk_variants_rev},
                {"hk_variants_rev_phrases", hk_variants_rev_phrases},
                {"jps_characters", jps_characters},
                {"jps_phrases", jps_phrases},
                {"jp_variants", jp_variants},
                {"jp_variants_rev", jp_variants_rev}
        };

        w.write("{");
        w.write(nl);
        for (Object[] f : fields) {
            firstField = writeField(w, (String) f[0], (DictEntry) f[1],
                    firstField, ind1, ind2, nl, sortKeys);
        }
        w.write(nl);
        w.write("}");
        w.write(nl);
    }

    /**
     * Writes a single {@link DictEntry} field to the JSON output.
     *
     * <p>Output format per field:</p>
     * <pre>{@code
     *   "name": [ { "k": "v", ... }, maxLength ]
     * }</pre>
     *
     * @param w          the {@link Writer} to write JSON to
     * @param name       the JSON field name (dictionary block name)
     * @param entry      the {@link DictEntry} containing the mapping and max length
     * @param firstField whether this is the first field in the enclosing object;
     *                   if {@code false}, a leading comma will be written before this field
     * @param ind1       indentation for the first level (used only if pretty-printing)
     * @param ind2       indentation for the second level (used only if pretty-printing)
     * @param nl         newline string (empty if not pretty-printing)
     * @param sortKeys   if {@code true}, dictionary keys will be written in sorted (UTF-16) order
     *                   for deterministic output; if {@code false}, keys will be written in
     *                   the map's natural iteration order
     * @return {@code false} to indicate that subsequent calls are not the first field anymore
     * @throws IOException if writing to the output fails
     *
     *                     <p><strong>Note:</strong> Sorting is used solely for deterministic serialization
     *                     (e.g., reproducible builds or test comparisons). It is not required for
     *                     functional correctness and may be disabled to preserve insertion order.</p>
     */
    private boolean writeField(
            Writer w, String name, DictEntry entry, boolean firstField,
            String ind1, String ind2, String nl, boolean sortKeys) throws IOException {

        if (entry == null) return firstField;

        if (!firstField) {
            w.write(",");
            w.write(nl);
        }
        w.write(ind1);
        w.write("\"");
        writeJsonString(name, w);
        w.write("\": [ ");
        w.write(nl);

        // object start
        w.write(ind2);
        w.write("{");
        w.write(nl);

        boolean firstKV = true;

        if (sortKeys) {
            // Deterministic: sort by key length (asc), then alphabetically
            String[] keys = entry.dict.keySet().toArray(new String[0]);
            java.util.Arrays.sort(keys, (a, b) -> {
                int d = a.length() - b.length();
                return (d != 0) ? d : a.compareTo(b);
            });

            for (String k : keys) {
                if (!firstKV) {
                    w.write(",");
                    w.write(nl);
                }
                w.write(ind2);
                w.write(ind1);
                w.write("\"");
                writeJsonString(k, w);
                w.write("\": ");
                w.write("\"");
                writeJsonString(entry.dict.get(k), w);
                w.write("\"");
                firstKV = false;
            }
        } else {
            for (java.util.Map.Entry<String, String> kv : entry.dict.entrySet()) {
                if (!firstKV) {
                    w.write(",");
                    w.write(nl);
                }
                w.write(ind2);
                w.write(ind1);
                w.write("\"");
                writeJsonString(kv.getKey(), w);
                w.write("\": ");
                w.write("\"");
                writeJsonString(kv.getValue(), w);
                w.write("\"");
                firstKV = false;
            }
        }

        w.write(nl);
        w.write(ind2);
        w.write("}");
        w.write(", ");
        w.write(String.valueOf(entry.maxLength));
        // NEW: always write minLength in new snapshots
        w.write(", ");
        w.write(Integer.toString(entry.minLength));
        w.write(" ]");
        return false;
    }

    /**
     * Minimal JSON string escaper. Writes directly to {@code w}.
     */
    private static void writeJsonString(String s, Writer w) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"':
                    w.write("\\\"");
                    break;
                case '\\':
                    w.write("\\\\");
                    break;
                case '\b':
                    w.write("\\b");
                    break;
                case '\f':
                    w.write("\\f");
                    break;
                case '\n':
                    w.write("\\n");
                    break;
                case '\r':
                    w.write("\\r");
                    break;
                case '\t':
                    w.write("\\t");
                    break;
                default:
                    if (c < 0x20) { // control char → \\u00XX
                        w.write("\\u00");
                        String hex = Integer.toHexString(c);
                        if (hex.length() == 1) w.write('0');
                        w.write(hex);
                    } else {
                        // write raw Unicode (Jackson may escape non-ASCII, but JSON allows raw UTF-8)
                        w.write(c);
                    }
            }
        }
    }

    // ===== NEW: union slots =====
    /**
     * Holds lazily computed {@link StarterUnion} instances for each {@link UnionKey}.
     * <p>
     * A {@code StarterUnion} represents the set of all possible starter characters
     * (first code points of dictionary keys) for a particular conversion union.
     * Unions are built only when first requested via {@link #unionFor(UnionKey)}
     * and cached in this container for reuse.
     * </p>
     */
    private Unions unions = new Unions();

    /**
     * Clears all cached {@link StarterUnion} instances.
     * <p>
     * After calling this method, all unions will be rebuilt lazily the next time
     * {@link #unionFor(UnionKey)} is invoked.
     * </p>
     */
    public void clearUnions() {
        this.unions = new Unions();
    }

    /**
     * Returns the {@link StarterUnion} for the given {@link UnionKey}.
     * <p>
     * If the union has not been computed yet, it is built from the relevant
     * dictionaries and cached. Subsequent calls with the same key return the
     * cached instance.
     * </p>
     *
     * <p>Union groupings:</p>
     * <ul>
     *   <li><b>S2T / T2S</b> – Simplified ↔ Traditional (with or without punctuation)</li>
     *   <li><b>Taiwan</b> – Phrase-only, variant-only, reverse pairs, and triple unions</li>
     *   <li><b>Hong Kong</b> – Variants-only and reverse pairs</li>
     *   <li><b>Japan</b> – Variants-only and reverse triples</li>
     * </ul>
     *
     * @param key the union key identifying which dictionaries to combine
     * @return the {@link StarterUnion} for the specified key
     * @throws IllegalArgumentException if the key is not recognized
     */
    public StarterUnion unionFor(UnionKey key) {
        switch (key) {
            // --- S2T / T2S ---
            case S2T:
                return getOrInit(unions.s2t,
                        () -> StarterUnion.build(Arrays.asList(st_phrases, st_characters)));
            case S2T_PUNCT:
                return getOrInit(unions.s2t_punct,
                        () -> StarterUnion.build(Arrays.asList(st_phrases, st_characters, st_punctuations)));

            case T2S:
                return getOrInit(unions.t2s,
                        () -> StarterUnion.build(Arrays.asList(ts_phrases, ts_characters)));
            case T2S_PUNCT:
                return getOrInit(unions.t2s_punct,
                        () -> StarterUnion.build(Arrays.asList(ts_phrases, ts_characters, ts_punctuations)));

            // --- TW ---
            case TwPhrasesOnly:
                return getOrInit(unions.tw_phrases_only,
                        () -> StarterUnion.build(Collections.singletonList(tw_phrases)));
            case TwVariantsOnly:
                return getOrInit(unions.tw_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(tw_variants)));
            case TwPhrasesRevOnly:
                return getOrInit(unions.tw_phrases_rev_only,
                        () -> StarterUnion.build(Collections.singletonList(tw_phrases_rev)));
            case TwRevPair:
                return getOrInit(unions.tw_rev_pair,
                        () -> StarterUnion.build(Arrays.asList(tw_variants_rev_phrases, tw_variants_rev)));
            case Tw2SpR1TwRevTriple:
                return getOrInit(unions.tw2sp_r1_tw_rev_triple,
                        () -> StarterUnion.build(Arrays.asList(tw_phrases_rev, tw_variants_rev_phrases, tw_variants_rev)));

            // --- HK ---
            case HkVariantsOnly:
                return getOrInit(unions.hk_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(hk_variants)));
            case HkRevPair:
                return getOrInit(unions.hk_rev_pair,
                        () -> StarterUnion.build(Arrays.asList(hk_variants_rev_phrases, hk_variants_rev)));

            // --- JP ---
            case JpVariantsOnly:
                return getOrInit(unions.jp_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(jp_variants)));
            case JpRevTriple:
                return getOrInit(unions.jp_rev_triple,
                        () -> StarterUnion.build(Arrays.asList(jps_phrases, jps_characters, jp_variants_rev)));

            default:
                throw new IllegalArgumentException("Unhandled UnionKey: " + key);
        }
    }

    // ---- tiny once-initializer for a fixed slot ----

    /**
     * Returns the {@link StarterUnion} stored in the given slot,
     * initializing it if necessary.
     * <p>
     * If the slot is empty, the supplied builder is invoked once to
     * construct a {@link StarterUnion}. The result is stored atomically
     * using {@link AtomicReference#compareAndSet(Object, Object)} to ensure
     * thread safety under concurrent access. If another thread initializes
     * the slot first, its value is returned instead.
     * </p>
     *
     * @param slot  the atomic slot holding the cached {@link StarterUnion}
     * @param build a factory to build the union if the slot is empty
     * @return the cached or newly built {@link StarterUnion}
     * @throws RuntimeException if the builder throws an exception
     */
    private static StarterUnion getOrInit(AtomicReference<StarterUnion> slot,
                                          java.util.concurrent.Callable<StarterUnion> build) {
        StarterUnion v = slot.get();
        if (v != null) return v;
        try {
            StarterUnion built = build.call();
            return slot.compareAndSet(null, built) ? built : slot.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Container for all per-dictionary {@link StarterUnion} slots.
     * <p>
     * Each field corresponds to a specific {@link UnionKey} and is backed
     * by an {@link AtomicReference}, allowing a union to be built once and
     * reused safely across threads. Unions are initialized lazily via
     * {@link #getOrInit(java.util.concurrent.atomic.AtomicReference, java.util.concurrent.Callable)}
     * the first time they are requested by {@link #unionFor(UnionKey)}.
     * </p>
     *
     * <p>
     * Using {@code AtomicReference} avoids synchronization on the entire
     * {@code Unions} container and ensures that each slot can be
     * independently and efficiently initialized under concurrent access.
     * </p>
     */
    static final class Unions {
        // S2T / T2S (+ punct)
        final AtomicReference<StarterUnion> s2t = new AtomicReference<>();
        final AtomicReference<StarterUnion> s2t_punct = new AtomicReference<>();
        final AtomicReference<StarterUnion> t2s = new AtomicReference<>();
        final AtomicReference<StarterUnion> t2s_punct = new AtomicReference<>();

        // TW
        final AtomicReference<StarterUnion> tw_phrases_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_phrases_rev_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_rev_pair = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw2sp_r1_tw_rev_triple = new AtomicReference<>();

        // HK
        final AtomicReference<StarterUnion> hk_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> hk_rev_pair = new AtomicReference<>();

        // JP
        final AtomicReference<StarterUnion> jp_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> jp_rev_triple = new AtomicReference<>();
    }

}
