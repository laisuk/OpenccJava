package openccjavawasm;

import java.util.Collection;
import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.resources.ResourcesPolicy;

/** Embeds the unchanged consolidated dictionary from the core project dependency. */
@Autoregistered
public final class DictionaryResources implements ResourcesPolicy {
    @Override
    public String[] supplyResources(Collection<? extends String> classes) {
        return new String[] { "dicts/dictionary_maxlength.json",
                "dicts/CJK_Compatibility_Ideographs.txt",
                "dicts/Unicode_Compatibility.txt", "dicts/CharactersTofu.txt" };
    }
}
