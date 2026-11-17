# Migration Guide

This document helps users migrate between versions of **`OpenCC Java`**.

---

## From v1.5.0 → v1.6.0

Existing public method signatures and Java 8 compatibility are unchanged.

### Small Seal Script

New configs `s2seal`, `t2seal`, `seal2s`, and `seal2t` are available through
`OpenccConfig`, configured conversion, and direct methods accepting `(String, boolean)`.
The boolean enables punctuation conversion independently of the selected config.
Seal output requires a supporting font, and dictionary conversion need not round-trip
to the original text.

Use the bundled v1.6.0 dictionaries for Seal conversion. Older text folders and JSON
snapshots remain loadable, but missing Seal slots are initialized as empty dictionaries;
no Seal mappings are added automatically. A mixed conversion such as `s2seal` can
still apply its Simplified-to-Traditional round when Seal slots are empty.

The new custom-dictionary slots are `SealCharacters`, `SealCharactersRev`,
`SealVariants`, and `SealVariantsRev`. Use canonical names instead of enum ordinals
when storing slot or configuration identifiers.

### Dictionary ownership

Constructors accepting `List<CustomDictSpec>` copy and patch the shared default
dictionary. `OpenCC.fromDicts(...)` and `DictionaryMaxlength.fromDicts(...)` load text
dictionaries independently. Treat dictionaries as immutable after attaching them to
converters or caches; create a customized copy and a new converter for changes.

---

## From v1.4.2 → v1.5.0

### Direct conversion methods

The direct methods `t2tw`, `t2twp`, `tw2t`, `tw2tp`, `t2hk`, `t2hkp`,
`hk2t`, `hk2tp`, `t2jp`, and `jp2t` now require a punctuation flag.
Replace calls such as `converter.t2tw(text)` with `converter.t2tw(text, false)`
to preserve previous behavior. Pass `true` to enable punctuation conversion.
This changes both source and binary compatibility: update and recompile callers.
The configured `convert(...)` signatures and default punctuation behavior are unchanged.

### Office and EPUB callbacks

Existing `OfficeHelper.convert(...)` overloads accepting `OpenCC` remain available.
New overloads accept `TextConverter` for a custom transformation pipeline. The callback
may receive whole XML/XHTML parts, including markup and entity references; preserve
their syntax. For XLSX worksheet inline strings, it receives the contents of individual
`<t>` elements. A null return or runtime exception produces a failed conversion result.

### Dictionaries and normalization

Treat dictionaries and cached plans as immutable after attaching them to a converter.
Use `CustomDictSpec` and `withCustomDicts(...)` to produce a separate customized dictionary.
Typed `Path` factories now preserve the original filesystem and filename; only the
textual `<slot>:<append|override>:<path>` parser trims surrounding whitespace.
JSON input-stream loaders close the supplied stream, including on parse failure.

The previously public `CompatIdeographs` class is now package-private, and its
nested `Map` and customization methods have been removed. This is also a source
and binary compatibility break. Replace built-in `CompatIdeographs.normalize(text)`
calls with `converter.normalizeCompat(text)`. There is no direct replacement for
custom compatibility maps or in-place `StringBuilder` normalization; callers using
those APIs must adapt their own transformation code before upgrading.
`normalizeUnicodeCompat(...)` and `normalizeCompatExtended(...)` add optional
normalization modes. The library continues to target Java 8.

---

## From v1.0.3 → v1.1.0

### 1. Static Dictionary

- In **v1.0.3**, each `OpenCC` instance could load its own dictionary.
- In **v1.1.0**, dictionaries are now **loaded once per JVM** (via `DictionaryHolder`) and shared by all instances.

#### Benefits

- Faster startup (dictionary parsing happens only once).
- Lower memory usage (one dictionary in memory instead of one per instance).
- Ideal for GUI applications (e.g. JavaFX) and helpers (e.g. `OfficeHelper`).

#### Logging

- **INFO** log: dictionary loaded from file system or embedded resource.
- **WARNING** log: dictionary fallback to plain-text sources.

⚠️ **Note:** The shared dictionary is effectively global.  
Any modifications will affect all `OpenCC` instances in the same JVM.

---

### 2. `zhoCheck` is now static

- In **v1.0.3**, `zhoCheck` was an instance method:
  ```java
  OpenCC cc = new OpenCC("s2t");
  int result = cc.zhoCheck("汉字");
    ```

- In **v1.1.0**, `zhoCheck` is a static method:

```java
int result = OpenCC.zhoCheck("汉字"); // preferred

```

- For backward compatibility, use` zhoCheckInstance`:

```java
OpenCC cc = new OpenCC("s2t");
int result = cc.zhoCheckInstance("汉字");
```

---

### 3. Recommended Usage

#### GUI apps (JavaFX, Swing)

Use the static dictionary for performance:

```java
OpenCC cc = new OpenCC("s2t"); // shares dictionary
String converted = cc.convert("汉字");
```

#### Office document helpers

Pass the `OpenCC` instance:

```java
OpenCC instance = new OpenCC("s2t");
OfficeHelper.Resault result = OfficeHelper.convert(inputPath, outputPath, "docx", instance, /* punctuation */ true, /* keepFont */ true);
```

### 4. When to Use Instance Dictionary

If you need to load a custom dictionary path, use the deprecated constructor:

```java
OpenCC custom = new OpenCC("s2t", Paths.get("my_dicts"));

```

This loads a private dictionary for that instance only.
It is slower and uses more memory but allows per-instance customization.

---

### Summary

- **Default**: use static dictionary + `OpenCC.zhoCheck()`.
- **Compatibility**: use `zhoCheckInstance()`.
- **Custom dictionary**: use the deprecated `(config, Path)` constructor.
