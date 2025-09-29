# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.0.3] - 2025-09-26

### Changed

- Update dictionaries
- Add minLength field to DictEntry
- Introduce bit-level delimiters check 

---

## [1.0.2] - 2025-08-31

### Changed

- Refactored all codes to be Java 8+ compatible

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

