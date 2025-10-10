# OpenccJava

[![Maven Central](https://img.shields.io/maven-central/v/io.github.laisuk/openccjava.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.laisuk/openccjava)
[![javadoc](https://javadoc.io/badge2/io.github.laisuk/openccjava/javadoc.svg)](https://javadoc.io/doc/io.github.laisuk/openccjava)
[![](https://jitpack.io/v/laisuk/OpenccJava.svg)](https://jitpack.io/#laisuk/OpenccJava)

**Pure Java**, **self-contained** implementation of **OpenCC** for **Traditional ↔ Simplified Chinese**
text conversion, with full **Office/EPUB** document support and a lightweight **CLI**.

## ✨ Features

- ✅ **High performance** – implements advanced optimization techniques for near-native speed:
    - **Static dictionaries** preloaded into memory for zero-overhead lookups.
    - **Starter index tables** accelerate segmentation by jumping directly to valid first characters.
    - **Starter masks** and **union masks** (bitwise-optimized) for constant-time gating of valid phrase starts.
    - **Starter unions** merge multiple dictionary sources efficiently, eliminating redundant scans.
    - **Length-aware segmentation** using per-starter `minLen` / `maxLen` limits to reduce iteration overhead.
    - **Zero-copy scanning** and **surrogate-safe UTF-16 iteration** to handle all BMP and non-BMP characters
      accurately.
    - **Pre-allocated buffers** and **minimal** `String` / `char[]` **allocations** to avoid GC overhead during large
      conversions.
    - **Fully parallelized dictionary initialization**, ensuring instant startup after first load.

  > Result: performance **on par with native OpenCC implementations (C++/Rust)**, verified with multi-million-character
  benchmarks.

- ✅ **Accurate with non-BMP CJK** – correctly handles astral Chinese characters (CJK Ext. B–G, U+20000+), using
  surrogate-safe scanning and max-match across code points.
- ✅ **Pure Java, no JNI** – easy to use on any JVM (JDK 1.8+), no native libraries required.
- ✅ **Wide format support** – convert plain text and Office documents: `.docx`, `.xlsx`, `.pptx`, `.odt`, `.epub`, etc.
- ✅ **Optional font name preservation** – keep original fonts when processing Office documents.
- ✅ **CLI tool included** (`OpenccJava.bat`) – convert files or stdin with flexible encoding options.
- ✅ **Cross-platform** – runs on Windows, Linux, and macOS without extra dependencies.
- ✅ **Self-contained** – no third-party dependencies, just the JDK.
- ✅ **Drop-in replacement for OpenCC configs** – supports the same dictionary structure and configs.

---

## 📦 Distribution

- Available on [Maven Central](https://search.maven.org/artifact/io.github.laisuk/openccjava)
  and [JitPack](https://jitpack.io/#laisuk/OpenccJava).
- Works with build tools like **Maven** and **Gradle**.
- CLI binaries included in GitHub releases.

---

## Modules

### `openccjava`

Reusable Java library for programmatic conversion.

## 🚀 Installation / Setup

### Option 1: Use from Maven Central

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation("io.github.laisuk:openccjava:1.1.0")
}
```

**Gradle (Groovy):**

```groovy
dependencies {
    implementation 'io.github.laisuk:openccjava:1.1.0'
}
```

**Maven:**

```xml

<dependency>
    <groupId>io.github.laisuk</groupId>
    <artifactId>openccjava</artifactId>
    <version>1.1.0</version>
</dependency>
```

### Option 2: Use with [JitPack](https://jitpack.io)

(Alternative if you want to build directly from GitHub tags)

**Gradle:**

```groovy
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
dependencies {
    implementation 'com.github.laisuk:OpenccJava:v1.1.0' // replace with latest tag
}
```

**Maven:**

```xml

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
<groupId>com.github.laisuk</groupId>
<artifactId>OpenccJava</artifactId>
<version>v1.1.0</version>
</dependency>
```

### Option 3: Clone and Build Locally

```bash
git clone https://github.com/laisuk/OpenccJava.git
cd OpenccJava
./gradlew build
```

- Built `.jar` will be in `openccjava/build/libs/`.
- CLI tools are built in `openccjavacli/build/libs/`.

### Option 3: Local Drop-In Module

If you don’t want to rely on `mavenCentral` or `JitPack`:

1. Copy the `openccjava/` folder into your own project.
2. Edit settings.gradle:

```groovy
include("openccjava")
```

1. Add to your dependencies:

```groovy
implementation project(":openccjava")
```

That’s it! No Internet required, no JARs to manage.

### 🧩 Required Dependencies

- `openccjava` (the core library) has **no external runtime dependencies**.
- `openccjavacli` (the command-line tool) bundles [picocli](https://picocli.info) automatically when built with
  `./gradlew distZip`.

👉 If you only use the library in your project (`io.github.laisuk:openccjava`), you don’t need to add anything else to
your classpath.

---

## 📖 OpenccJava API Usage

`OpenCC` provides conversion between Simplified and Traditional Chinese (and variants) using configurable dictionaries.

### 🔧 Constructor Overloads

```java
OpenCC cc = new OpenCC();                         // Uses default config "s2t", auto-load dicts
OpenCC cc = new OpenCC("tw2sp");                  // Specify config
// @deprecated
OpenCC cc = new OpenCC("s2t", Path.of("dicts"));  // Load custom plain-text dicts from folder
```

### 🔤 Basic Conversion

```java
import openccjava.OpenCC;

public class Example {
    static void main(String[] args) {
        OpenCC converter = new OpenCC("s2t");
        String result = converter.convert("汉字转换");
        System.out.println(result); // → 漢字轉換 
    }
}
```

With punctuation conversion:

```java
String result = converter.convert("“春眠不觉晓”", true);
// → 「春眠不覺曉」
```

### ⚙️ Configuration & Error Handling

```java
import openccjava.OpenCC;

public class Example {
    static void main(String[] args) {
        OpenCC cc = new OpenCC();
        cc.setConfig("t2s");
        String cfg = cc.getConfig();           // → "t2s"
        String err = cc.getLastError();        // → null or last failure reason
    }
}
```

### 🧠 Auto Variant Detection

> **Migration Notes**: `zhoCheck()` will be refactored into a static method in the next major release (planned for
> v1.1.0).  
> At that time, it should be invoked as `Opencc.zhoCheck()`.

```java
import openccjava.OpenCC;

public class Example {
    static void main(String[] args) {
        int code = OpenCC.zhoCheck("漢字");  // returns 1 → 1 = Traditional, 2 = Simplified, 0 = Unknown/Mixed
    }
}
```

### 🔄 Direct Conversion APIs (Optional Use)

```java
import openccjava.OpenCC;

public class Example {
    static void main(String[] args) {
        OpenCC cc = new OpenCC();
        cc.s2t("汉字");            // 漢字 - Simplified → Traditional
        cc.t2s("漢字");            // 汉字 - Traditional → Simplified
        cc.s2tw("汉字");           // 漢字 - Simplified → Taiwan Traditional
        cc.tw2sp("臺灣計程車");          // 台湾出租车 - Taiwan Traditional → Simplified with idioms
        cc.t2jp("傳統");           // 伝統 - Traditional → Japanese Kanji
    }
}
```

Most methods support boolean punctuation as a second parameter.

### 📚 Supported Config Keys

```java
List<String> configs = OpenCC.getSupportedConfigs();
```

Includes:

```
s2t, t2s, s2tw, tw2s, s2twp, tw2sp,
s2hk, hk2s, t2tw, t2twp, t2hk,
tw2t, tw2tp, hk2t, t2jp, jp2t
```

### 📋 Verbose Logging (optional for debug)

```java
import openccjava.OpenCC;

public class Example {
    static void main(String[] args) {
        OpenCC.setVerboseLogging(true);
    }
}

```

Logs will print dictionary load source and fallback behavior. Useful for CLI or testing. Disabled by default to keep GUI
silent.

---

### 🧩 Example – Converting a .docx Document

```java
import openccjava.OpenCC;
import openccjava.OfficeHelper;
import openccjava.OfficeHelper.Result;

import java.io.File;

public class Example {
    static void main(String[] args) {
        // Input and output files
        File input = new File("example_simplified.docx");
        File output = new File("example_traditional.docx");

        // Create an OpenCC converter (Simplified → Traditional)
        OpenCC converter = new OpenCC("s2t");

        // Convert the document
        Result result = OfficeHelper.convert(
                input,
                output,
                "docx",      // Supported: docx, xlsx, pptx, odt, epub
                converter,
                true,        // Convert punctuation
                true         // Preserve font names
        );

        // Show result
        if (result.success) {
            System.out.println("✅ Conversion successful: " + output.getAbsolutePath());
        } else {
            System.err.println("❌ Conversion failed: " + result.message);
        }
    }
}

```

---

### `openccjavacli`

Command-line tool based on `openccjava`.

#### Build

```bash
./gradlew distZip
```

Zip file will be created in: `openccjavacli/build/distributions/openccjavacli-<version>.zip`

#### Run (after extracting)

```bash
bin/OpenccJavaCli.bat convert -c s2t -i input.txt -o output.txt
```

```bash
bin/OpenccJavaCli convert --help                                                           
Usage: openccjavacli convert [-hpV] [--list-configs] -c=<conversion>
                             [--con-enc=<encoding>] [-i=<file>]
                             [--in-enc=<encoding>] [-o=<file>]
                             [--out-enc=<encoding>]
Convert plain text using OpenccJava
  -c, --config=<conversion>  Conversion configuration
      --con-enc=<encoding>   Console encoding for interactive mode. Ignored if
                               not attached to a terminal. Common <encoding>:
                               UTF-8, GBK, Big5
  -h, --help                 Show this help message and exit.
  -i, --input=<file>         Input file
      --in-enc=<encoding>    Input encoding
      --list-configs         List all supported OpenccJava conversion
                               configurations
  -o, --output=<file>        Output file
      --out-enc=<encoding>   Output encoding
  -p, --punct                Punctuation conversion (default: false)
  -V, --version              Print version information and exit.
```

#### Office document conversion:

```bash
bin/OpenccJavaCli.bat office -c s2t -i book.docx -o book_converted.docx
```

```bash
bin/OpenccJavaCli office --help 
Usage: opencccli office [-hpV] [--auto-ext] [--[no-]keep-font] -c=<conversion>
                        [--format=<format>] -i=<file> [-o=<file>]
Convert Office documents using OpenccJava
      --auto-ext          Auto-append extension to output file
  -c, --config=<conversion>
                          Conversion configuration
      --format=<format>   Target Office format (e.g., docx, xlsx, pptx, odt,
                            epub)
  -h, --help              Show this help message and exit.
  -i, --input=<file>      Input Office file
      --[no-]keep-font    Preserve font-family info (default: false)
  -o, --output=<file>     Output Office file
  -p, --punct             Punctuation conversion (default: false)
  -V, --version           Print version information and exit.
```

#### Optional flags:

- `--punct`: Enable punctuation conversion.
- `--auto-ext`: Auto-append extension like `_converted`.
- `--keep-font` / `--no-keep-font`: Preserve original fonts (Office only).
- `--in-enc` / `--out-enc`: Specify encoding (e.g. `GBK`, `BIG5`, `UTF-8`).
- `--format`: Force document format (e.g., `docx`, `epub`).
- `--list-configs`: Show supported OpenCC configs.

#### OpenccJava Dictionary Generator

Generate JSON dictionary from raw `dicts/*.txt`

```bash
OpenccJavaCli dictgen --help
Usage: opencccli dictgen [-hV] [-f=<format>] [-o=<filename>]
Generate dictionary for OpenccJava
  -f, --format=<format>     Dictionary format: [json]
  -h, --help                Show this help message and exit.
  -o, --output=<filename>   Output filename
  -V, --version             Print version information and exit.
```

---

## 🧾 Encodings (Charsets)

- **Linux/macOS**: Terminals are UTF-8 by default. You usually don’t need to set anything.
- **Windows**: The console isn’t always UTF-8. If you’re piping or using non-UTF-8 files, set encodings explicitly using
  the CLI flags below.

> 💡 Tip for Windows users:  
> If you have enabled “**Beta: Use Unicode UTF-8 for worldwide language support**” in
_Control Panel → Region → Administrative → Language for non-Unicode programs → Change system locale_,
> your console already uses UTF-8 — no need to specify `--con-enc UTF-8`.
> You can safely display emoji, Chinese, and other Unicode characters without needing to run `chcp 65001` or modify code
> pages manually.

### CLI flags (recommended)

- `--in-enc <name>`   : Charset for reading input files (default: UTF-8)
- `--out-enc <name>`  : Charset for writing output files (default: UTF-8)
- `--con-enc <name>`  : Charset for *console* stdin/stdout on Windows (default: UTF-8)

> The charset `<name>` is any value accepted by Java’s `Charset.forName(...)`.  
> Names are **case-insensitive** and aliases are supported.

---

### 🧪 Example (Windows console behavior)

#### 🔹 Without “Beta: Unicode UTF-8” enabled

```bash
C:\> openccjava --text "你好，世界 🌏✨"
Output: 你好，世界 ??   ← (emoji not displayed correctly)
```

#### 🔹 With “Beta: Unicode UTF-8” enabled

```bash
C:\> openccjava --text "你好，世界 🌏✨"
Output: 你好，世界 🌏✨
```

✅ Characters and emoji display properly — no need for extra `--con-enc` flags or chcp commands.

---

#### Common charsets (quick list)

- **Unicode**: `UTF-8`, `UTF-16`, `UTF-16LE`, `UTF-16BE`
- **Chinese (Traditional/Simplified)**: `Big5`, `Big5-HKSCS`, `GBK`, `GB18030`, `GB2312`
- **Japanese**: `Shift_JIS`, `windows-31j` (aka MS932), `EUC-JP`, `ISO-2022-JP`
- **Korean**: `EUC-KR`, `MS949` (aka `x-windows-949`)
- **SE Asia**: `TIS-620` (Thai), `windows-1258` (Vietnamese)
- **Cyrillic**: `windows-1251`, `KOI8-R`, `KOI8-U`, `ISO-8859-5`
- **Western Europe**: `ISO-8859-1`, `windows-1252`, `ISO-8859-15`
- **Others (selected)**: `ISO-8859-2/3/4/7/8/9/13/16`, `windows-1250/1253/1254/1255/1256/1257`

> Tip: `GB2312` is commonly an alias handled via `EUC-CN`/`GBK` on modern JDKs. Prefer `GBK` or `GB18030`.

### Examples

```bash
# Linux/macOS (files)
openccjavacli convert -c t2s -i in_big5.txt --in-enc Big5 -o out_utf8.txt --out-enc UTF-8

# Windows (pipe Big5 into the tool, keep console in GBK)
Get-Content .\in_big5.txt -Encoding Big5 | openccjavacli.bat convert -c t2s -p --con-enc GBK

# Force UTF-8 console on Windows (PowerShell 7+)
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
openccjavacli.bat convert -c s2t -p --con-enc UTF-8
```

---

### ⚡ Benchmark

| Test Case                 | Input Size      | Platform                                      | Time              | Notes                             |
|---------------------------|-----------------|-----------------------------------------------|-------------------|-----------------------------------|
| Simplified → Traditional  | 3 million chars | Intel i5-13400 @ 2.5 GHz (Win 11 x64, JDK 21) | **≈ 80 – 150 ms** | Comparable to native coded OpenCC |
| Traditional → Simplified  | 3 million chars | Same setup                                    | **≈ 90 – 160 ms** | Slight variation due to GC cycles |
| Office Document (`.docx`) | 1 MB XML text   | Same setup                                    | **< 200 ms**      | Includes XML parse + repack       |

> 🧩 Benchmarks were performed with UTF-8 input/output, GC logging enabled, and stable performance across Windows, Linux,
> and macOS.  
> Actual results depend on JVM version, heap size, and available CPU threads.

---

## 📚 Documentation

This project supports parallel processing for high-performance batch conversion.

👉 Read more: [🚀 Parallel Data Processing Notes](NOTES.md)

---

## Projects That Use `openccjava`

[OpenccJavaFX](https://github.com/laisuk/OpenccJavaFX)

---

## License

- MIT License.
- © Laisuk.
- See [LICENSE](./LICENSE) for details.
- See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) for bundled OpenCC lexicons (Apache License 2.0).
