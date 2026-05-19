package openccjava;

import java.nio.file.Path;
import java.util.*;

public final class CustomDictSpec {
    public final DictSlot slot;
    public final List<Path> paths;
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

    public static CustomDictSpec fromFile(DictSlot slot, Path path, CustomDictMode mode) {
        return new CustomDictSpec(slot, Collections.singletonList(
                Objects.requireNonNull(path, "path")
        ), mode);
    }

    public static CustomDictSpec fromFiles(DictSlot slot, List<Path> paths, CustomDictMode mode) {
        return new CustomDictSpec(slot, paths, mode);
    }
}