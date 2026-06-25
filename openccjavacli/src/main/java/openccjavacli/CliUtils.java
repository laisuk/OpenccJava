package openccjavacli;

import openccjava.*;

import java.nio.file.Paths;
import java.util.*;

public final class CliUtils {
    private CliUtils() {
    }

    static OpenCC createOpenCC(
            String config,
            List<String> customDictSpecs
    ) {
        OpenccConfig typedConfig = OpenccConfig.tryParse(config);
        if (typedConfig == null) {
            typedConfig = OpenccConfig.defaultConfig();
        }

        if (customDictSpecs == null || customDictSpecs.isEmpty()) {
            return new OpenCC(typedConfig);
        }

        List<CustomDictSpec> specs = new ArrayList<>();
        for (String raw : customDictSpecs) {
            specs.add(parseCustomDictSpec(raw));
        }

        DictionaryMaxlength dict = DictionaryMaxlength.fromDicts(specs);
        return new OpenCC(typedConfig, dict);
    }

    static CustomDictSpec parseCustomDictSpec(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty --custom-dict spec");
        }

        String[] parts = raw.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid --custom-dict spec: " + raw +
                            " (expected slot:append|override:path)"
            );
        }

        DictSlot slot = parseDictSlot(parts[0]);
        CustomDictMode mode = parseCustomDictMode(parts[1]);
        return CustomDictSpec.fromFile(slot, Paths.get(parts[2]), mode);
    }

    private static final Map<String, DictSlot> SLOT_LOOKUP = createSlotLookup();

    private static Map<String, DictSlot> createSlotLookup() {
        Map<String, DictSlot> map = new HashMap<>();

        for (DictSlot slot : DictSlot.values()) {
            map.put(normalize(slot.name()), slot);
        }

        return Collections.unmodifiableMap(map);
    }

    private static String normalize(String value) {
        return value
                .trim()
                .replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
    }

    static DictSlot parseDictSlot(String value) {
        DictSlot slot = SLOT_LOOKUP.get(normalize(value));
        if (slot == null) {
            throw new IllegalArgumentException("Invalid custom dict slot: " + value);
        }
        return slot;
    }

    static CustomDictMode parseCustomDictMode(String value) {
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("append".equals(v)) return CustomDictMode.Append;
        if ("override".equals(v)) return CustomDictMode.Override;
        throw new IllegalArgumentException("Invalid custom dict mode: " + value);
    }
}
