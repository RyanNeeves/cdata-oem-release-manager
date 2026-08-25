package com.cdata.embeddeddrivers.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class RootCommandTest {

    @Test
    void versionOptionPrintsVersion() {
        StringWriter out = new StringWriter();
        CommandLine cmd = RootCommand.buildCommandLine();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("--version");

        assertEquals(0, exitCode);
        assertTrue(out.toString().startsWith("cdrm "), "version output should start with 'cdrm '");
    }

    @Test
    void helpOptionExitsCleanly() {
        StringWriter out = new StringWriter();
        CommandLine cmd = RootCommand.buildCommandLine();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode);
        assertTrue(out.toString().contains("Usage: cdrm"));
    }

    @Test
    void downloadRequiresRelease() {
        StringWriter err = new StringWriter();
        CommandLine cmd = RootCommand.buildCommandLine();
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("download", "-e", "jdbc", "-c", "salesforce");

        assertEquals(2, exitCode, "missing --release should be a usage error");
    }

    @Test
    void downloadHelpListsEditionDisplayNames() {
        StringWriter out = new StringWriter();
        CommandLine cmd = RootCommand.buildCommandLine();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("download", "--help");

        assertEquals(0, exitCode);
        // picocli wraps help text at 80 columns, so compare with collapsed whitespace
        String help = out.toString().replaceAll("\\s+", " ");
        // derived from Edition.displayNames(), not hand-maintained
        assertTrue(help.contains("ADO .NET FRAMEWORK"),
                "edition help should list the enum's display names");
        assertTrue(help.contains("the default when no --connector is given"),
                "help should state that --all is the default");
    }

    @Test
    void downloadRejectsOutOfRangeParallel() {
        StringWriter err = new StringWriter();
        CommandLine cmd = RootCommand.buildCommandLine();
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("download", "-e", "jdbc", "-r", "latest", "-p", "99");

        assertEquals(2, exitCode, "out-of-range --parallel should be a usage error");
        assertTrue(err.toString().contains("--parallel must be between 1 and 16"), err.toString());
    }

    @Test
    void downloadRejectsConnectorCombinedWithAll() {
        StringWriter err = new StringWriter();
        CommandLine cmd = RootCommand.buildCommandLine();
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("download", "-e", "jdbc", "-r", "latest", "-c", "salesforce", "--all");

        assertEquals(2, exitCode, "--connector and --all together should be a usage error");
    }

    @Test
    void unknownEditionIsUsageErrorWithLenientParserMessage() {
        StringWriter err = new StringWriter();
        CommandLine cmd = RootCommand.buildCommandLine();
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("download", "-e", "cobol", "-r", "latest", "--all");

        assertEquals(2, exitCode);
        assertTrue(err.toString().contains("Unknown edition"),
                "registered Edition converter should produce the lenient parser's message");
    }
}
