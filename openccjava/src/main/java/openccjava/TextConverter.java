package openccjava;

/**
 * Represents a caller-supplied transformation for text-bearing content inside
 * an Office or EPUB package.
 *
 * <p>{@link OfficeHelper} owns package parsing, ZIP reconstruction, entry
 * selection, XLSX inline-string handling, EPUB packaging rules, and optional
 * font protection. A {@code TextConverter} owns only the transformation
 * applied to selected text.</p>
 *
 * <p>This separation allows callers to compose OpenCC conversion with other
 * processing steps, such as compatibility normalization or DeToFu, without
 * coupling those policies to the Office/EPUB package layer.</p>
 *
 * <p>The interface is deliberately compatible with Java 8 lambdas and method
 * references.</p>
 *
 * <pre>{@code
 * TextConverter textConverter = converter::convert;
 * }</pre>
 */
@FunctionalInterface
public interface TextConverter {

    /**
     * Transforms one text fragment selected by the Office/EPUB package layer.
     *
     * @param text decoded text fragment; never {@code null}
     * @return transformed text; must not be {@code null}
     */
    String convert(String text);
}
