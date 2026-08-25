package com.cdata.releasemanager.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.cdata.releasemanager.cli.commands.ChangelogCommand;
import com.cdata.releasemanager.cli.commands.ConnectorsCommand;
import com.cdata.releasemanager.cli.commands.DownloadCommand;
import com.cdata.releasemanager.cli.commands.ReleasesCommand;
import com.cdata.releasemanager.core.Edition;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "cdrm",
        description = "CData Release Manager - manage releases of CData embedded/OEM drivers.",
        mixinStandardHelpOptions = true,
        versionProvider = RootCommand.VersionProvider.class,
        subcommands = {
                ReleasesCommand.class,
                ConnectorsCommand.class,
                ChangelogCommand.class,
                DownloadCommand.class,
        })
public class RootCommand implements Runnable {

    @Spec
    CommandSpec spec;

    public static void main(String[] args) {
        System.exit(buildCommandLine().execute(args));
    }

    /** The full command tree with lenient Edition parsing registered. Shared by main and tests. */
    public static CommandLine buildCommandLine() {
        return new CommandLine(new RootCommand()).registerConverter(Edition.class, Edition::parse);
    }

    @Override
    public void run() {
        // No subcommand given: show usage.
        spec.commandLine().usage(System.out);
    }

    /**
     * Reads the version from version.properties, which Maven filters at build
     * time so the jar always reports its true artifact version.
     */
    static class VersionProvider implements IVersionProvider {

        @Override
        public String[] getVersion() {
            Properties props = new Properties();
            try (InputStream in = VersionProvider.class.getResourceAsStream("/version.properties")) {
                if (in != null) {
                    props.load(in);
                }
            } catch (IOException e) {
                // Fall through to defaults.
            }
            String version = props.getProperty("version", "dev");
            return new String[] {
                    "cdrm " + version,
                    "Java " + System.getProperty("java.version")
                            + " (" + System.getProperty("os.name")
                            + "/" + System.getProperty("os.arch") + ")"
            };
        }
    }
}
