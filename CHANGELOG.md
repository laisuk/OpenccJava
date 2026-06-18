# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.4.0] - 2029-06-19

### Added

* Added optional DeTofu display-compatibility fallback support for rare non-BMP CJK extension characters.
* Added built-in DeTofu fallback mappings embedded in `dicts/TSCharactersTofu.txt`.
* Added `DeTofu` utility API for applying display-compatible fallback substitutions after OpenCC conversion.
* Added `DeTofu.Level` extension threshold levels:

    * `ExtB`
    * `ExtC`
    * `ExtD`
    * `ExtE`
    * `ExtF`
    * `ExtG`
    * `ExtH`
    * `ExtI`
* Added `OpenCC.deTofu(...)` convenience API.
* Added `OpenCC.deTofuWithCustomFile(...)` convenience API.
* Added support for custom UTF-8 DeTofu fallback files that extend or override built-in mappings.
* Added DeTofu regression tests covering:

    * Built-in fallback mappings.
    * OpenCC conversion + DeTofu integration.
    * Custom fallback file overrides.
    * Preservation of unmapped extension characters.
* Added direct Hong Kong phrase-level conversion configs and APIs:

    * `OpenccConfig.S2HKP` / config key `s2hkp`.
    * `OpenccConfig.HK2SP` / config key `hk2sp`.
    * `OpenCC.s2hkp(String, boolean)` for Simplified → Hong Kong Traditional with HK phrase normalization.
    * `OpenCC.hk2sp(String, boolean)` for Hong Kong Traditional phrase/variant normalization → Simplified.
* Added Hong Kong phrase dictionary slots:

    * `DictSlot.HKPhrases` backed by `HKPhrases.txt`.
    * `DictSlot.HKPhrasesRev` backed by `HKPhrasesRev.txt`.

### Changed

* Update and optimize dictionary data to reduce conversion ambiguity.
* Added forward regional variant phrase dictionary slots:

    * `DictSlot.TWVariantsPhrases` backed by `TWVariantsPhrases.txt`.
    * `DictSlot.HKVariantsPhrases` backed by `HKVariantsPhrases.txt`.
* Updated TW/HK forward regional variant conversion to apply phrase-level variant dictionaries before character-level
  variants:

    * `tw_variants_phrases` before `tw_variants`.
    * `hk_variants_phrases` before `hk_variants`.
* Updated `DictionaryMaxlength` JSON hydration, serialization, copying, strict text-dictionary loading, and custom
  dictionary patching for `tw_variants_phrases` and `hk_variants_phrases`.
* Updated `DictionaryMaxlength` text loading and JSON hydration so missing `HKPhrases.txt`, `HKPhrasesRev.txt`, or older
  JSON snapshots without `hk_phrases` / `hk_phrases_rev` initialize those slots as empty dictionaries.
* Refactored JP conversion to follow the upstream OpenCC Shinjitai dictionary layout from commit `93ee7f78`.
* Updated `t2jp` to use required `JPShinjitaiCharactersRev.txt` data only.
* Updated `jp2t` to use `JPShinjitaiPhrases.txt` before `JPShinjitaiCharacters.txt`.
* Deprecated legacy custom dictionary slots `DictSlot.JPVariants` and `DictSlot.JPVariantsRev`; they remain as
  compatibility aliases until version 2.0.
* Added custom dictionary slot `DictSlot.JPSCharactersRev` backed by `JPShinjitaiCharactersRev.txt`.
* Users who maintain custom dictionary folders or generated `dictionary_maxlength.json` snapshots must regenerate them
  after upgrading to the new JP dictionary layout. Required JP files are `JPShinjitaiCharacters.txt`,
  `JPShinjitaiCharactersRev.txt`, and `JPShinjitaiPhrases.txt`.
* Renamed internal forward TW/HK variant union keys from `TwVariantsOnly` / `HkVariantsOnly` to `TwVariantsPair` /
  `HkVariantsPair`.
* Preserved reverse TW/HK variant ordering through existing reverse phrase + character dictionaries.
* Added flexible JSON serialization overloads with configurable pretty-printing and deterministic key sorting.
* Added deterministic lexical key sorting support for JSON dictionary serialization.
* Improved no-dependency JSON serialization API organization while preserving backward compatibility.
* Refactored existing `serializeToJson*NoDeps()` APIs to delegate to the new configurable serialization overloads.
* Improved JSON serialization documentation and README usage examples.

### CLI

* Added `--detofu <level>` option to `convert` for applying DeTofu display-compatible fallback mappings after OpenCC
  conversion.
* Added `--detofu-file <file>` option to `convert` for loading additional UTF-8 DeTofu fallback mappings. Custom
  mappings override built-in mappings.
* Supported DeTofu levels:

    * `all`
    * `ext-b`
    * `ext-c`
    * `ext-d`
    * `ext-e`
    * `ext-f`
    * `ext-g`
    * `ext-h`
    * `ext-i`
* Added `-s` / `--sort` option to `dictgen` for deterministic lexical JSON key ordering.
* Added support for combining `--compact` and `--sort` in `dictgen`.
* Updated `dictgen` dictionary download list to include `TWVariantsPhrases.txt` and `HKVariantsPhrases.txt`.
* Preserved previous default `dictgen` behavior (`pretty + sorted`) for backward compatibility.

---

## [1.3.0] - 2026-05-24

### Added

* Added user custom dictionary support for `openccjava`.
* Added construction-time custom dictionary injection via:

    * `DictionaryMaxlength.fromDicts(...)`
    * `OpenCC.fromDicts(...)`
* Added post-load custom dictionary customization via:

    * `DictionaryMaxlength.withCustomDicts(...)`
    * `DictionaryMaxlength.withCustomDictFiles(...)`
* Added `CustomDictSpec` for immutable custom dictionary patch specifications.
* Added `CustomDictSpec.fromFile(...)` and `CustomDictSpec.fromFiles(...)` for UTF-8 OpenCC text dictionary loading.
* Added `CustomDictSpec.fromPairs(...)` for in-memory custom dictionary mappings.
* Added `DictSlot` enum for type-safe dictionary slot selection.
* Added `CustomDictMode` with:

    * `Append`
    * `Override`
* Added support for applying multiple custom dictionary specs in order.
* Added support for file-level and in-memory pair-level custom dictionary merging.
* Added defensive immutable handling for custom dictionary paths and pairs.
* Added unit tests covering:

    * append mode
    * override mode
    * construction-time customization
    * post-load customization
    * file-based custom dictionaries
    * in-memory pair-based custom dictionaries

### Changed

* Updated dictionary data.
* Refactored dictionary customization pipeline to support reusable custom dictionary spec application.
* Unified file-based and pair-based custom dictionary handling under shared internal application logic.
* Improved dictionary entry rebuilding after custom dictionary application.
* Improved Javadoc documentation for custom dictionary APIs and behaviors.
* Updated `README.md` with comprehensive user custom dictionary documentation and examples.

---

## [1.2.3] - 2026-05-11

### Changed

- Refreshed dictionary data
- Synced latest phrase mappings and conversion tables
- Improved conversion accuracy and consistency

### Notes

- No API changes
- No behavioral breaking changes
- Fully compatible with previous 1.2.x releases

---

## [1.2.2] - 2026-04-18

### Changed

- Update conversion dictionary data.
- optimized `OfficeHelper` for handling `XLSX` `inline-string`.
- CLI: Optimized `Reflow`.
- `OfficeHelper.convert(File, File, ...)` now rejects `null` output targets instead of returning a misleading success
  result.
- Keep `UnionCache` internal/package-private so it is not exposed as part of the supported public API surface.
- `MiniDictJson` now accepts legacy two-element dictionary snapshot arrays (`[map, maxLength]`) while continuing to
  support the current three-element form with `minLength`.
- Refined Javadoc across `OpenCC`, `DictionaryMaxlength`, and `MiniDictJson` to match current runtime behavior and JSON
  schema.
- Refactored `DictionaryMaxlength.toString()` to remove repetitive counting logic while preserving output.

---

## [1.2.1] - 2026-04-03

### Changed

- Update dictionary data.
- Optimized `UnionCache`.
- Optimized core conversion.
- Optimized OfficeHelper.
- Corrected sample codes for `MemoryResult` in README.
- Removed legacy deprecated `getDictRefs()`.
- Optimized `openccjavacli` Reflow to handle unclosed dialog text.

---

## [1.2.0] - 2026-02-21

### Added

- Added support PDF file type as input in `openccjavacli` subcommand `pdf`

### Changed

- `OpenccConfig` as single source of truth for conversion configuration while compatible with legacy config.
- Update dictionary to v1.2.0

---

## [1.1.1] - 2025-11-25

### Changed

- Refactored `OfficeHelper` to include a core `byte[]`-based `convert()` API for in-memory document processing.
- Updated conversion result handling: introduced unified abstract `Result` base class with concrete `FileResult` and
  `MemoryResult` subtypes.
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

