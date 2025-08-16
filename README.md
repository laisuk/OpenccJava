# OpenccJava

[![Maven Central](https://img.shields.io/maven-central/v/io.github.laisuk/openccjava.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.laisuk/openccjava)
[![](https://jitpack.io/v/laisuk/OpenccJava.svg)](https://jitpack.io/#laisuk/OpenccJava)

**Java** port of **OpenCC** with **Office/EPUB** document support and **CLI** tools.

## Features

- ✅ High-performance OpenCC-compatible conversion in pure Java.
- ✅ Supports plain text and Office formats: `.docx`, `.xlsx`, `.pptx`, `.odt`, `.epub`, etc.
- ✅ Optional font name preservation during Office document conversion.
- ✅ CLI tool (`OpenccJava.bat`) for converting files or stdin with flexible encoding options.

---

## Modules

### `openccjava`

Reusable Java library for programmatic conversion.

## 🚀 Installation / Setup

### Option 1: Use from Maven Central

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("io.github.laisuk:openccjava:1.0.1")
}
```

**Gradle (Groovy):**
```groovy
dependencies {
    implementation 'io.github.laisuk:openccjava:1.0.1'
}
```

**Maven:**
```xml
<dependency>
  <groupId>io.github.laisuk</groupId>
  <artifactId>openccjava</artifactId>
  <version>1.0.1</version>
</dependency>
```

### Option 2: Use with [JitPack](https://jitpack.io)
(Alternative if you want to build directly from GitHub tags)

**Gradle:**
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.laisuk:OpenccJava:openccjava-v1.0.0' // replace with latest tag
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
  <version>openccjava-v1.0.0</version>
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

If you don’t want to rely on JitPack:

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
- `openccjavacli` (the command-line tool) bundles [picocli](https://picocli.info) automatically when built with `./gradlew distZip`.

👉 If you only use the library in your project (`io.github.laisuk:openccjava`), you don’t need to add anything else to your classpath.

---

## 📖 OpenccJava API Usage

`OpenCC` provides conversion between Simplified and Traditional Chinese (and variants) using configurable dictionaries.

### 🔧 Constructor Overloads

```java
OpenCC cc = new OpenCC();                         // Uses default config "s2t", auto-load dicts
OpenCC cc = new OpenCC("tw2sp");                  // Specify config
OpenCC cc = new OpenCC("s2t", Path.of("dicts"));  // Load custom plain-text dicts from folder
```

### 🔤 Basic Conversion

```java
import openccjava.OpenCC;

public class Example {
    public static void main(String[] args) {
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
    public static void main(String[] args) {
        OpenCC cc = new OpenCC();
        cc.setConfig("t2s");
        String cfg = cc.getConfig();           // → "t2s"
        String err = cc.getLastError();        // → null or last failure reason
    }
}
```

### 🧠 Auto Variant Detection

```java
import openccjava.OpenCC;

public class Example {
    public static void main(String[] args) {
        OpenCC cc = new OpenCC();
        int code = cc.zhoCheck("漢字");  // returns 1 → 1 = Traditional, 2 = Simplified, 0 = Unknown/Mixed
    }
}
```

### 🔄 Direct Conversion APIs (Optional Use)

```java
import openccjava.OpenCC;

public class Example {
    public static void main(String[] args) {
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
    public static void main(String[] args) {
        OpenCC.setVerboseLogging(true);
    }
}

```

Logs will print dictionary load source and fallback behavior. Useful for CLI or testing. Disabled by default to keep GUI
silent.

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
Usage: opencccli convert [-hpV] [--list-configs] -c=<conversion> [-i=<file>]
                         [--in-enc=<encoding>] [-o=<file>]
                         [--out-enc=<encoding>]
Convert plain text using OpenccJava
  -c, --config=<conversion>  Conversion configuration
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

## Encoding Notes

CLI console input/output on some non UTF-8 encoded console:

- On **Windows Simplified Chinese**, use:  
  `--in-enc=GBK --out-enc=GBK`
- On **Traditional Chinese Windows**, use:  
  `--in-enc=BIG5 --out-enc=BIG5`
- UTF-8 terminals (Linux/macOS/Windows Terminal with `chcp 65001`) work without flags.

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