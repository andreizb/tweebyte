package ro.tweebyte.analysis;

import picocli.CommandLine;

@CommandLine.Command(
        name = "analysis",
        subcommands = {IngestCommand.class, PlotCommand.class, ReportCommand.class},
        description = "Benchmark result ingestion, per-cell stats, and PNG/CSV figures.")
public class AnalysisCli implements Runnable {

    public static void main(String[] args) {
        int exit = new CommandLine(new AnalysisCli()).execute(args);
        System.exit(exit);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
