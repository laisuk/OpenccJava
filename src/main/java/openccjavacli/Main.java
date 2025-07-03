package openccjavacli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "opencccli",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Pure Java OpenCC CLI with multiple tools",
        subcommands = {
                ConvertCommand.class,
                DictgenCommand.class
        }
)
public class Main implements Runnable {

    @Override
    public void run() {
        // Called when no subcommand is provided
        System.out.println("Use a subcommand: convert | dictgen");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
