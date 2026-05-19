package openccjava;

import java.nio.file.Path;
import java.util.*;

/**
 * Describes one custom dictionary patch operation.
 *
 * <p>A spec selects one {@link DictSlot}, one or more UTF-8 OpenCC dictionary
 * text files, and a {@link CustomDictMode}. The files are parsed with the same
 * parser used for built-in OpenCC text dictionaries: one entry per line,
 * source, a tab, then target text; blank lines and comment lines follow the
 * existing parser behavior, and only the first target token is used.</p>
 *
 * <p>Instances are immutable. The {@link #paths} list is defensively copied and
 * exposed as an unmodifiable list.</p>
 *
 * @see DictionaryMaxlength#fromDicts(java.util.List)
 * @see DictionaryMaxlength#withCustomDictFiles(java.util.List)
 */
public final class CustomDictSpec {
    /**
     * The dictionary slot that this spec patches.
     */
    public final DictSlot slot;

    /**
     * UTF-8 dictionary text files to apply, in order.
     */
    public final List<Path> paths;

    /**
     * How the custom files are applied to the selected slot.
     */
    public final CustomDictMode mode;

    private CustomDictSpec(DictSlot slot, List<Path> paths, CustomDictMode mode) {
        this.slot = Objects.requireNonNull(slot, "slot");
        this.paths = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(paths, "paths")
        ));
        this.mode = Objects.requireNonNull(mode, "mode");

        if (this.paths.isEmpty()) {
            throw new IllegalArgumentException("paths must not be empty");
        }
    }

    /**
     * Creates a spec for one custom dictionary file.
     *
     * <p>The file is parsed later, when the spec is passed to
     * {@link DictionaryMaxlength#fromDicts(java.util.List)},
     * {@link DictionaryMaxlength#fromDicts(String, java.util.List)}, or
     * {@link DictionaryMaxlength#withCustomDictFiles(java.util.List)}.</p>
     *
     * @param slot the dictionary slot to patch; must not be {@code null}
     * @param path the UTF-8 OpenCC dictionary text file; must not be {@code null}
     * @param mode append or override behavior; must not be {@code null}
     * @return an immutable custom dictionary spec
     * @throws NullPointerException if {@code slot}, {@code path}, or {@code mode} is {@code null}
     */
    public static CustomDictSpec fromFile(DictSlot slot, Path path, CustomDictMode mode) {
        return new CustomDictSpec(slot, Collections.singletonList(
                Objects.requireNonNull(path, "path")
        ), mode);
    }

    /**
     * Creates a spec for multiple custom dictionary files applied in order.
     *
     * <p>All files are parsed with the same parser used for built-in OpenCC
     * text dictionaries. Later files win when they define the same source key
     * as earlier files in the same spec. The input list is defensively copied.</p>
     *
     * @param slot the dictionary slot to patch; must not be {@code null}
     * @param paths UTF-8 OpenCC dictionary text files; must not be {@code null}
     *              or empty
     * @param mode append or override behavior; must not be {@code null}
     * @return an immutable custom dictionary spec
     * @throws NullPointerException if {@code slot}, {@code paths}, or {@code mode} is {@code null}
     * @throws IllegalArgumentException if {@code paths} is empty
     */
    public static CustomDictSpec fromFiles(DictSlot slot, List<Path> paths, CustomDictMode mode) {
        return new CustomDictSpec(slot, paths, mode);
    }
}
