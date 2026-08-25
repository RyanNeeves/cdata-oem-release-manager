package com.cdata.releasemanager.cli.commands;

import java.util.List;
import java.util.concurrent.Callable;

import com.cdata.releasemanager.core.OemBuildsClient;
import com.cdata.releasemanager.core.Release;

import picocli.CommandLine.Command;

@Command(
        name = "releases",
        mixinStandardHelpOptions = true,
        description = "List available CData connector releases, newest first.")
public class ReleasesCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        List<Release> releases = new OemBuildsClient().listReleases();
        if (releases.isEmpty()) {
            System.out.println("No releases found.");
            return 0;
        }
        System.out.println("Latest: " + releases.get(0).label());
        System.out.println("Available releases (newest first):");
        for (Release r : releases) {
            System.out.printf("  %s  (major version: %d, release number: %d)%n",
                    r.label(), r.year(), r.releaseNumber());
        }
        return 0;
    }
}
