# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.1.1.1] - 2025-12-23

### Added
- Added PDF file type supported as input in `openccjavacli` subcommand `pdf`

---

## [1.1.1] - 2025-11-25

### Changed
- Refactored `OfficeHelper` to include a core `byte[]`-based `convert()` API for in-memory document processing.
- Updated conversion result handling: introduced unified abstract `Result` base class with concrete `FileResult` and `MemoryResult` subtypes.
- Ensured backward compatibility: legacy `Result` return type remains valid and unchanged for existing users.

---

## [1.1.0] - 2025-10-10

### Added

- Introduced `StarterIndex` and `UnionCache` to speed up conversion.
- Added `OpenCC.setVerboseLogging(boolean)` to enable or disable runtime logging.
    - When enabled, informational messages about dictionary loading, fallbacks, and diagnostics are printed to the
      console.
    - By default, logging is disabled to keep GUI and CLI outputs clean.

### Changed

- **Static dictionary implementation**:  
  Dictionaries are now loaded once per JVM (lazy-loaded via `DictionaryHolder`) and shared by all `OpenCC` instances.
    - Improves startup performance and reduces memory usage for GUI apps (e.g. JavaFX) and helper classes (e.g.
      `OfficeHelper`).
    - Log messages are emitted on first load:
        - **INFO** when loaded from file system or embedded resource.
        - **WARNING** when falling back to plain-text dictionary sources.

- `OpenCC.zhoCheck(String)` is now a **static method** for clarity and consistency.  
  Existing instance calls `myOpenCC.zhoCheck(text)` will no longer compile.  
  Use one of:
    - `OpenCC.zhoCheck(text)` – preferred static usage.
    - `myOpenCC.zhoCheckInstance(text)` – for backward-compatible instance style.

- Added **Starter Length Mask** for faster dictionary lookup and reduced branching.

### Migration Notes

#### Before (v1.0.3)

```java
OpenCC cc = new OpenCC("s2t");
int result = cc.zhoCheck("汉字");
```

### After (v1.1.0)

```java
// Preferred static usage
int result = OpenCC.zhoCheck("汉字");

// Or for compatibility with old instance style
OpenCC cc = new OpenCC("s2t");
int result = cc.zhoCheckInstance("汉字");

```

> ⚠️ Note: The dictionary is now shared across all OpenCC instances.  
> Any modification to the dictionary object will affect all instances in the JVM.

---

## [1.0.3] - 2025-09-30

### Changed

- Update dictionaries
- DictionaryMaxlength: computes and stores `minLength`.
- JSON writer now emits [map,max,min]
- Performance: bitset `isDelimiter()`; faster `getSplitRanges()`.
- No public API breaks.

### Announced

- New JSON blocks: `st_punctuations`, `ts_punctuations` ([ {dict}, maxLength, minLength ]).
- These are reserved for the next major and not used by runtime conversion yet.
- `translatePunctuation()` is deprecated and will be removed in v1.1.0.
- `zhoCheck()` will be changed from instance to static function in next major version (v1.1.0).

---

## [1.0.2] - 2025-08-31

### Changed

- Refactored all codes to be **Java 8+** compatible

### Fixed

- Artifacts compiled as Java 17+ in GitHub Action (should be Java 11+)

---

## [1.0.1] - 2025-08-16

### Changed

- Dropped all Jackson JSON dependencies to reduce JAR size and improve startup time.
- Implemented `MiniDictJson`, a minimal, schema-specific JSON parser and serializer for `DictionaryMaxlength`.
- `DictionaryMaxlength.fromJsonNoDeps()` and related methods now use `MiniDictJson` internally.
- `serializeToJsonNoDeps()` and `serializeToJsonStringNoDeps()` added for Jackson-free JSON export.
- Code refactored for **JDK 11+ compatibility** (removed enhanced `switch` syntax and newer language constructs).
- All CLI and library functions verified to work without Jackson present on the classpath.
- Resulting JARs (`openccjava` and `openccjavacli`) are now fully self-contained and **module-info friendly**.

---

## [1.0.0] - 2025-07-30

### Added

- 🎉 First official release of `OpenccJava` to [JitPack.io](https://jitpack.io/#laisuk/OpenccJava)
- ✅ Core `openccjava` module: pure Java implementation of OpenCC-compatible converter
- ✅ Support for OpenCC configuration strings (e.g., `"s2t"`, `"tw2sp"`)
- ✅ Dictionary loader from default or custom `dicts/` folder
- ✅ Optional punctuation conversion (`convert(text, true)`)
- ✅ CLI support via `openccjavacli` module (e.g., `OpenccJava.bat`)
- ✅ Office and EPUB document conversion (DOCX, XLSX, PPTX, ODT, EPUB)
- ✅ Optional font-name preservation in converted Office files
- ✅ Compatible with JDK 17+ (tested with OpenJDK 17 and 21)
- ✅ Published example usage, CLI docs, and integration notes in README

---

