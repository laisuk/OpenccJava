package openccjavacli;

import openccjava.DictionaryMaxlength;
import picocli.CommandLine.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Command(name = "dictgen", description = "\033[1;34mGenerate dictionary for OpenccJava\033[0m", mixinStandardHelpOptions = true)
public class DictgenCommand implements Runnable {

    @Option(names = {"-f", "--format"}, description = "Dictionary format: [json]", defaultValue = "json")
    private String format;

    @Option(names = {"-o", "--output"}, paramLabel = "<filename>", description = "Output filename")
    private String output;

    private static final Logger LOGGER = Logger.getLogger(DictgenCommand.class.getName());
    private static final String BLUE = "\033[1;34m";
    private static final String RESET = "\033[0m";

    @Override
    public void run() {
        try {
            File dictFolder = new File("dicts");
            if (!dictFolder.exists() || !dictFolder.isDirectory()) {
                System.out.print(BLUE + "Local 'dicts/' not found, proceed to download from GitHub otherwise use built-in dictionaries (Y/n): " + RESET);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String input = reader.readLine().trim().toLowerCase();

                if (input.isEmpty() || input.equals("y") || input.equals("yes")) {
                    downloadDictsFromGithub(dictFolder.toPath());
                } else {
                    System.out.println("Using built-in dictionaries.");
                }
            }

            String defaultOutput = "json".equals(format) ? "dictionary_maxlength.json" : null;
            if (defaultOutput == null) {
                LOGGER.severe("Unsupported format: " + format);
                System.exit(1);
            }

            String outputFile = (output != null) ? output : defaultOutput;
            File outputPath = Paths.get(outputFile).toAbsolutePath().toFile();

            DictionaryMaxlength dicts = DictionaryMaxlength.fromDicts(); // Uses either file or built-in

            if ("json".equals(format)) {
                dicts.serializeToJsonNoDeps(outputPath.getAbsolutePath());
                System.out.println(BLUE + "Dictionary saved in JSON format at: " + outputPath + RESET);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception during dictionary generation", e);
            System.exit(1);
        }
    }

    private void downloadDictsFromGithub(Path dictDir) throws IOException {
        String[] dictFiles = {
                "STCharacters.txt", "STPhrases.txt", "TSCharacters.txt", "TSPhrases.txt",
                "TWPhrases.txt", "TWPhrasesRev.txt", "TWVariants.txt", "TWVariantsRev.txt", "TWVariantsRevPhrases.txt",
                "HKVariants.txt", "HKVariantsRev.txt", "HKVariantsRevPhrases.txt",
                "JPShinjitaiCharacters.txt", "JPShinjitaiPhrases.txt", "JPVariants.txt", "JPVariantsRev.txt"
        };

        String baseUrl = "https://raw.githubusercontent.com/laisuk/OpenccJava/master/dicts/";

        Files.createDirectories(dictDir);

        for (String fileName : dictFiles) {
            String fileUrl = baseUrl + fileName;
            Path outputPath = dictDir.resolve(fileName);

            System.out.print(BLUE + "Downloading: " + fileName + "... " + RESET);

            try {
                // Use URI to parse and construct the URL string
                URI uri = new URI(fileUrl);
                URL url = uri.toURL(); // Convert URI to URL

                try (InputStream in = url.openStream()) { // Now openStream on the URL
                    Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("done");
                }
            } catch (URISyntaxException e) {
                System.out.println("Failed to create URI for " + fileName);
                LOGGER.warning("Failed to create URI for " + fileUrl + ": " + e.getMessage());
            } catch (IOException e) {
                System.out.println("Failed to download " + fileName);
                LOGGER.warning("Failed to download " + fileUrl + ": " + e.getMessage());
            }
        }
    }
}
