package ro.tweebyte.calibration;

import picocli.CommandLine;

@CommandLine.Command(
        name = "calibration",
        subcommands = {CollectSamplesCommand.class, ValidateMockCommand.class, RefitCommand.class},
        description = "LLM calibration + mock validation toolkit (Apache Commons Math-backed).")
public class CalibrationCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CalibrationCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
