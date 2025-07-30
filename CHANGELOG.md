# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/).

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

