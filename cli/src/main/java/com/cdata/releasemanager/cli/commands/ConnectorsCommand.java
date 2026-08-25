package com.cdata.releasemanager.cli.commands;

import java.util.List;
import java.util.concurrent.Callable;

import com.cdata.releasemanager.cli.EditionCandidates;
import com.cdata.releasemanager.core.Edition;
import com.cdata.releasemanager.core.OemBuildsClient;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "connectors",
        mixinStandardHelpOptions = true,
        description = "List the connectors available for an edition and major version.")
public class ConnectorsCommand implements Callable<Integer> {

    @Option(names = {"-e", "--edition"}, required = true, completionCandidates = EditionCandidates.class,
            description = "Driver edition: ${COMPLETION-CANDIDATES}.")
    Edition edition;

    @Option(names = {"-v", "--major-version"},
            description = "Major version year (e.g. 2025). Defaults to the latest release's major version. "
                    + "Run 'cdrm releases' to see available versions.")
    Integer majorVersion;

    @Override
    public Integer call() throws Exception {
        OemBuildsClient client = new OemBuildsClient();
        int version = majorVersion != null ? majorVersion : client.latestRelease().year();

        List<String> connectors;
        try {
            connectors = client.listConnectors(edition, version);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage() + " Run 'cdrm releases' to see available versions.");
            return 1;
        }

        if (connectors.isEmpty()) {
            System.out.println("No connectors found for " + edition.displayName()
                    + " in major version " + version + ".");
            return 0;
        }

        System.out.printf("%d connector%s available for %s in major version %d:%n",
                connectors.size(), connectors.size() == 1 ? "" : "s", edition.displayName(), version);
        for (String name : connectors) {
            System.out.println("  " + name);
        }
        return 0;
    }
}
