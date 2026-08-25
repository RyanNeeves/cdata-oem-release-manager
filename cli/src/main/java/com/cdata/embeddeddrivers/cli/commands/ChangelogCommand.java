package com.cdata.embeddeddrivers.cli.commands;

import java.util.concurrent.Callable;

import com.cdata.embeddeddrivers.cli.EditionCandidates;
import com.cdata.embeddeddrivers.core.Edition;
import com.cdata.embeddeddrivers.core.OemBuildsClient;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "changelog",
        mixinStandardHelpOptions = true,
        description = "Show changelog entries for a connector since a release, date, or build.")
public class ChangelogCommand implements Callable<Integer> {

    @Option(names = {"-e", "--edition"}, required = true, completionCandidates = EditionCandidates.class,
            description = "Driver edition: ${COMPLETION-CANDIDATES}.")
    Edition edition;

    @Option(names = {"-c", "--connector"}, required = true,
            description = "Connector name (e.g. Salesforce). Run 'cdrm connectors' to list valid names.")
    String connector;

    @Option(names = {"-v", "--major-version"},
            description = "Major version year (e.g. 2025). Each major version has its own changelog. "
                    + "Defaults to the latest release's major version.")
    Integer majorVersion;

    @ArgGroup(multiplicity = "1")
    Baseline baseline;

    static class Baseline {
        @Option(names = "--after-release",
                description = "Show entries after this release: a U-number within the queried "
                        + "major version (e.g. 2 for U2), or a full release in any major version "
                        + "(e.g. 2025u3) for cross-version baselines.")
        String afterRelease;

        @Option(names = "--after-date",
                description = "Show entries after this date (YYYY-MM-DD).")
        String afterDate;

        @Option(names = "--after-build",
                description = "Show entries after this build number.")
        Integer afterBuild;
    }

    @Override
    public Integer call() throws Exception {
        OemBuildsClient client = new OemBuildsClient();
        try {
            int version = majorVersion != null ? majorVersion : client.latestRelease().year();
            System.out.println(client.changelogReport(edition, version, connector,
                    baseline.afterRelease, baseline.afterDate, baseline.afterBuild));
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Run 'cdrm connectors' to see valid connector names, or 'cdrm releases' for releases.");
            return 1;
        }
    }
}
