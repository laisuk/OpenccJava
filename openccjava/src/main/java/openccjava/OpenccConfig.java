package openccjava;

/**
 * Supported conversion configurations.
 *
 * <p>Each constant corresponds to an OpenCC conversion mode,
 * covering Simplified ↔ Traditional Chinese and region-specific variants
 * (Taiwan, Hong Kong, Japan). The {@code p} suffix indicates that
 * phrase-level mappings are also applied.</p>
 */
public enum OpenccConfig {

    /**
     * Simplified → Traditional.
     */
    S2T,

    /**
     * Traditional → Simplified.
     */
    T2S,

    /**
     * Simplified → Traditional (Taiwan).
     */
    S2Tw,

    /**
     * Traditional (Taiwan) → Simplified.
     */
    Tw2S,

    /**
     * Simplified → Traditional (Taiwan, with phrases).
     */
    S2Twp,

    /**
     * Traditional (Taiwan, with phrases) → Simplified.
     */
    Tw2Sp,

    /**
     * Simplified → Traditional (Hong Kong).
     */
    S2Hk,

    /**
     * Traditional (Hong Kong) → Simplified.
     */
    Hk2S,

    /**
     * Traditional → Traditional (Taiwan).
     */
    T2Tw,

    /**
     * Traditional → Traditional (Taiwan, with phrases).
     */
    T2Twp,

    /**
     * Traditional (Taiwan) → Traditional.
     */
    Tw2T,

    /**
     * Traditional (Taiwan, with phrases) → Traditional.
     */
    Tw2Tp,

    /**
     * Traditional → Traditional (Hong Kong).
     */
    T2Hk,

    /**
     * Traditional (Hong Kong) → Traditional.
     */
    Hk2T,

    /**
     * Traditional → Japanese Shinjitai.
     */
    T2Jp,

    /**
     * Japanese Shinjitai → Traditional.
     */
    Jp2T;

    /**
     * Returns the lowercase string form of this config.
     * <p>
     * Example: {@code S2T.asStr()} → {@code "s2t"}.
     * </p>
     *
     * @return the lowercase string representation
     */
    public String asStr() {
        return name().toLowerCase();
    }

    /**
     * Parses a string into a corresponding {@code Config} constant, ignoring case.
     *
     * <p>This method performs a case-insensitive lookup of the provided value
     * against all available configuration constants. It supports both upper-
     * and lower-case forms, including mixed-case variants such as
     * {@code "s2twp"} or {@code "T2Twp"}.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * Config c1 = Config.fromStr("s2t");    // returns Config.S2T
     * Config c2 = Config.fromStr("T2Twp");  // returns Config.T2Twp
     * Config c3 = Config.fromStr("tw2tp");  // returns Config.Tw2Tp
     * }</pre>
     *
     * @param value the configuration key string (e.g., {@code "s2t"}, {@code "t2s"},
     *              {@code "t2twp"}, {@code "tw2tp"})
     * @return the matching {@code Config} constant
     * @throws IllegalArgumentException if {@code value} is {@code null} or does not
     *                                  correspond to any known configuration constant
     */
    public static OpenccConfig fromStr(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Config string cannot be null");
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Config string cannot be empty");
        }

        for (OpenccConfig c : OpenccConfig.values()) {
            if (c.name().equalsIgnoreCase(trimmed)
                    || c.asStr().equalsIgnoreCase(trimmed)) {
                return c;
            }
        }

        throw new IllegalArgumentException("Unknown config: " + value);
    }
}
