# OpenccJava

Java port of OpenCC with Office/EPUB document support and CLI tools.

## Features

- ✅ High-performance OpenCC-compatible conversion in pure Java.
- ✅ Supports plain text and Office formats: `.docx`, `.xlsx`, `.pptx`, `.odt`, `.epub`, etc.
- ✅ Optional font name preservation during Office document conversion.
- ✅ CLI tool (`OpenccJava.bat`) for converting files or stdin with flexible encoding options.

## Modules

### `openccjava`

Reusable Java library for programmatic conversion.

#### Usage

```java 
import openccjava.core.OpenCC;

public class Example {
    public static void main(String[] args) {
        OpenCC converter = new OpenCC("s2t");
        String result = converter.convert("汉字转换");
        System.out.println(result); // → 漢字轉換 
    }
}
```

You can also set punctuation conversion:

```java
String result = converter.convert("“你好！”", true);
```

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
bin/OpenccJava.bat convert --office -c s2t -i book.docx -o book_converted.docx
```

#### Optional flags:

- `--punct`: Enable punctuation conversion.
- `--auto-ext`: Auto-append extension like `_converted`.
- `--keep-font` / `--no-keep-font`: Preserve original fonts (Office only).
- `--in-enc` / `--out-enc`: Specify encoding (e.g. `GBK`, `BIG5`, `UTF-8`).
- `--format`: Force document format (e.g., `docx`, `epub`).
- `--list-configs`: Show supported OpenCC configs.

## Encoding Notes

- On **Windows Simplified Chinese**, use:  
  `--in-enc=GBK --out-enc=GBK`
- On **Traditional Chinese Windows**, use:  
  `--in-enc=BIG5 --out-enc=BIG5`
- UTF-8 terminals (Linux/macOS/Windows Terminal with `chcp 65001`) work without flags.

## License

- MIT License.
- © Laisuk.
- See [LICENSE](./LICENSE) for details.