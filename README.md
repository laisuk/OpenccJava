# OpenccJava

Java port of OpenCC with Office/EPUB document support and CLI tools.

## Features

- ✅ High-performance OpenCC-compatible conversion in pure Java.
- ✅ Supports plain text and Office formats: `.docx`, `.xlsx`, `.pptx`, `.odt`, `.epub`, etc.
- ✅ Optional font name preservation during Office document conversion.
- ✅ CLI tool (`OpenccJava.bat`) for converting files or stdin with flexible encoding options.

---

## Modules

### `openccjava`

Reusable Java library for programmatic conversion.

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

Logs will print dictionary load source and fallback behavior. Useful for CLI or testing. Disabled by default to keep GUI silent.

---

### `opencccli`

Command-line tool based on `openccjava`.

#### Build

```bash
./gradlew distZip
```

Zip file will be created in: `build/distributions/opencccli-<version>.zip`

#### Run (after extracting)

```bash
bin/OpenccJava.bat convert -c s2t -i input.txt -o output.txt
```

#### Office document conversion:

```bash
bin/OpenccJava.bat office -c s2t -i book.docx -o book_converted.docx
```

#### Optional flags:

- `--punct`: Enable punctuation conversion.
- `--auto-ext`: Auto-append extension like `_converted`.
- `--keep-font` / `--no-keep-font`: Preserve original fonts (Office only).
- `--in-enc` / `--out-enc`: Specify encoding (e.g. `GBK`, `BIG5`, `UTF-8`).
- `--format`: Force document format (e.g., `docx`, `epub`).
- `--list-configs`: Show supported OpenCC configs.

## Encoding Notes

During CLI console input/output on some non UTF-8 encoded console:

- On **Windows Simplified Chinese**, use:  
  `--in-enc=GBK --out-enc=GBK`
- On **Traditional Chinese Windows**, use:  
  `--in-enc=BIG5 --out-enc=BIG5`
- UTF-8 terminals (Linux/macOS/Windows Terminal with `chcp 65001`) work without flags.

## License

- MIT License.
- © Laisuk.
- See [LICENSE](./LICENSE) for details.