package openccjava;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DeTofu {
    private static final String BUILTIN_RESOURCE = "/dicts/TSCharactersTofu.txt";

    private static final List<Entry> BUILTIN_ENTRIES = loadBuiltinEntries();

    private DeTofu() {
    }

    public enum Level {
        ExtB, ExtC, ExtD, ExtE, ExtF, ExtG, ExtH, ExtI;

        public boolean accepts(Level entryLevel) {
            return entryLevel.ordinal() >= this.ordinal();
        }

        public static Level parse(String value) {
            if (value == null)
                throw new NullPointerException("value");

            switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "all":
                case "ext-b":
                case "b":
                    return ExtB;
                case "ext-c":
                case "c":
                    return ExtC;
                case "ext-d":
                case "d":
                    return ExtD;
                case "ext-e":
                case "e":
                    return ExtE;
                case "ext-f":
                case "f":
                    return ExtF;
                case "ext-g":
                case "g":
                    return ExtG;
                case "ext-h":
                case "h":
                    return ExtH;
                case "ext-i":
                case "i":
                    return ExtI;
                default:
                    throw new IllegalArgumentException(
                            "Supported DeTofu levels: all, ext-b, ext-c, ext-d, ext-e, ext-f, ext-g, ext-h, ext-i."
                    );
            }
        }
    }

    public static String convert(String input, Level level) {
        return Map.builtin(level).convert(input);
    }

    public static Map builtinMap(Level level) {
        return Map.builtin(level);
    }

    private static List<Entry> loadBuiltinEntries() {
        InputStream stream = DeTofu.class.getResourceAsStream(BUILTIN_RESOURCE);
        if (stream == null)
            return Collections.emptyList();

        try {
            return parseEntries(readUtf8(stream));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static String readUtf8(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }

        return builder.toString();
    }

    private static List<Entry> parseEntries(String text) {
        List<Entry> entries = new ArrayList<>();

        if (text == null || text.isEmpty())
            return entries;

        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#"))
                    continue;

                String[] parts = line.split("\t");
                if (parts.length < 3)
                    continue;

                String tofu = readFirstScalar(parts[0].trim());
                String fallback = readFirstScalar(parts[1].trim());
                Level ext = tryParseExtension(parts[2]);

                if (tofu != null && fallback != null && ext != null)
                    entries.add(new Entry(tofu, fallback, ext));
            }
        } catch (IOException ignored) {
            // StringReader should not throw in normal use.
        }

        return entries;
    }

    private static Level tryParseExtension(String value) {
        if (value == null)
            return null;

        switch (value.trim()) {
            case "ExtB":
            case "B":
            case "b":
                return Level.ExtB;
            case "ExtC":
            case "C":
            case "c":
                return Level.ExtC;
            case "ExtD":
            case "D":
            case "d":
                return Level.ExtD;
            case "ExtE":
            case "E":
            case "e":
                return Level.ExtE;
            case "ExtF":
            case "F":
            case "f":
                return Level.ExtF;
            case "ExtG":
            case "G":
            case "g":
                return Level.ExtG;
            case "ExtH":
            case "H":
            case "h":
                return Level.ExtH;
            case "ExtI":
            case "I":
            case "i":
                return Level.ExtI;
            default:
                return null;
        }
    }

    private static String readFirstScalar(String value) {
        if (value == null || value.isEmpty())
            return null;

        int first = value.codePointAt(0);
        return new String(Character.toChars(first));
    }

    private static final class Entry {
        final String tofu;
        final String fallback;
        final Level extension;

        Entry(String tofu, String fallback, Level extension) {
            this.tofu = tofu;
            this.fallback = fallback;
            this.extension = extension;
        }
    }

    public static final class Map {
        private final Level level;
        private final HashMap<String, String> map;

        private Map(Level level, HashMap<String, String> map) {
            this.level = level;
            this.map = map;
        }

        public static Map builtin(Level level) {
            if (level == null)
                throw new NullPointerException("level");

            HashMap<String, String> map = new HashMap<>();

            for (Entry entry : BUILTIN_ENTRIES) {
                if (level.accepts(entry.extension))
                    map.put(entry.tofu, entry.fallback);
            }

            return new Map(level, map);
        }

        public Map withCustomFile(String path) throws IOException {
            if (path == null)
                throw new NullPointerException("path");

            String text = new String(
                    java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                    StandardCharsets.UTF_8
            );

            return withEntries(parseEntries(text));
        }

        public Map withCustomPairs(java.util.Map<String, String> pairs) {
            if (pairs == null)
                throw new NullPointerException("pairs");

            for (java.util.Map.Entry<String, String> pair : pairs.entrySet()) {
                String tofu = readFirstScalar(pair.getKey());
                String fallback = readFirstScalar(pair.getValue());

                if (tofu != null && fallback != null)
                    map.put(tofu, fallback);
            }

            return this;
        }

        public String convert(String input) {
            if (input == null || input.isEmpty())
                return "";

            StringBuilder sb = new StringBuilder(input.length());

            for (int i = 0; i < input.length(); ) {
                int cp = input.codePointAt(i);
                i += Character.charCount(cp);

                String ch = new String(Character.toChars(cp));

                String replacement = map.get(ch);
                sb.append(replacement != null ? replacement : ch);
            }

            return sb.toString();
        }

        private Map withEntries(List<Entry> entries) {
            for (Entry entry : entries) {
                if (level.accepts(entry.extension))
                    map.put(entry.tofu, entry.fallback);
            }

            return this;
        }
    }
}