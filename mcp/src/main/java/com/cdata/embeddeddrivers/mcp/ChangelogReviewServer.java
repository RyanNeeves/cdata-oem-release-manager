package com.cdata.embeddeddrivers.mcp;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import com.cdata.embeddeddrivers.core.Edition;
import com.cdata.embeddeddrivers.core.OemBuildsClient;
import com.cdata.embeddeddrivers.core.Release;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * CData Changelog Review — MCP Server.
 * Thin MCP layer over embedded-drivers-core; see README.md for setup.
 */
public class ChangelogReviewServer {

    private static final OemBuildsClient CLIENT = new OemBuildsClient();

    /** The project version, filtered into version.properties by Maven at build time. */
    private static String version() {
        Properties props = new Properties();
        try (InputStream in = ChangelogReviewServer.class.getResourceAsStream("/version.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            // Fall through to the default.
        }
        return props.getProperty("version", "dev");
    }

    // ============================================================
    //  SHARED UTILITIES
    // ============================================================

    private static CallToolResult ok(String text) {
        return CallToolResult.builder()
                .content(Collections.singletonList((McpSchema.Content) new TextContent(text)))
                .isError(false)
                .build();
    }

    private static CallToolResult err(String message) {
        return CallToolResult.builder()
                .content(Collections.singletonList((McpSchema.Content) new TextContent(message)))
                .isError(true)
                .build();
    }

    // ============================================================
    //  SCHEMA HELPERS
    // ============================================================

    private static Map<String, Object> schemaProperty(String type, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> schemaEnum(String description, Collection<String> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        m.put("enum", new ArrayList<>(values));
        return m;
    }

    // ============================================================
    //  ARGUMENT PARSING
    // ============================================================

    private static String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer optIntArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            String sv = s.trim();
            return sv.isEmpty() ? null : Integer.parseInt(sv);
        }
        return null;
    }

    private static int requireMajorVersion(Map<String, Object> args) {
        Integer mv = optIntArg(args, "major_version");
        if (mv == null) {
            throw new IllegalArgumentException("major_version is required. Call list_releases to see available major versions.");
        }
        return mv;
    }

    private static Edition requireEdition(Map<String, Object> args) {
        String editionRaw = stringArg(args, "edition");
        if (editionRaw == null) {
            throw new IllegalArgumentException("edition is required.");
        }
        return Edition.parse(editionRaw);
    }

    private static String appendConnectorHint(String message, Edition edition, int majorVersion) {
        return message + " Call list_connectors with edition='" + edition.displayName()
                + "' and major_version=" + majorVersion + " to see all valid connector names.";
    }

    // ============================================================
    //  TOOL: list_releases
    // ============================================================

    private static McpSchema.JsonSchema listReleasesSchema() {
        return new McpSchema.JsonSchema("object",
                Collections.emptyMap(), Collections.emptyList(), null, null, null);
    }

    private static CallToolResult handleListReleases(Map<String, Object> args) {
        try {
            List<Release> releases = CLIENT.listReleases();
            if (releases.isEmpty()) return ok("No releases found.");
            StringBuilder sb = new StringBuilder("Latest release: " + releases.get(0).label() + "\n");
            sb.append("All releases (newest first), with the get_changelog arguments for each:\n");
            for (Release r : releases) {
                sb.append(String.format("  %s  (major_version: %d, after_release: \"%du%d\")%n",
                        r.label(), r.year(), r.year(), r.releaseNumber()));
            }
            return ok(sb.toString().stripTrailing());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return err("Error listing releases: " + e.getMessage());
        }
    }

    // ============================================================
    //  TOOL: list_connectors
    // ============================================================

    private static McpSchema.JsonSchema listConnectorsSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("edition", schemaEnum("Driver edition.", Edition.displayNames()));
        props.put("major_version", schemaProperty("integer", "Major version year from list_releases (e.g. 2025)."));
        return new McpSchema.JsonSchema("object", props,
                Arrays.asList("edition", "major_version"), null, null, null);
    }

    private static CallToolResult handleListConnectors(Map<String, Object> args) {
        int majorVersion;
        Edition edition;
        try {
            majorVersion = requireMajorVersion(args);
            edition = requireEdition(args);
        } catch (IllegalArgumentException e) {
            return err(e.getMessage());
        }

        List<String> connectors;
        try {
            connectors = CLIENT.listConnectors(edition, majorVersion);
        } catch (IllegalArgumentException e) {
            return err(e.getMessage() + " Call list_releases to see available major versions.");
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return err("Error listing connectors: " + e.getMessage());
        }

        if (connectors.isEmpty()) {
            return ok("No connectors found for " + edition.displayName() + " in major version " + majorVersion + ".");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d connector%s available for %s in major version %d (use one verbatim as connector_name in get_changelog):%n",
                connectors.size(), connectors.size() == 1 ? "" : "s", edition.displayName(), majorVersion));
        for (String s : connectors) {
            sb.append("  ").append(s).append('\n');
        }
        return ok(sb.toString().stripTrailing());
    }

    // ============================================================
    //  TOOL: get_changelog
    // ============================================================

    private static McpSchema.JsonSchema getChangelogSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("edition",        schemaEnum("Driver edition.", Edition.displayNames()));
        props.put("connector_name", schemaProperty("string",  "Connector name (e.g. Salesforce)"));
        props.put("major_version",  schemaProperty("integer", "Major version year of the changelog to read, from list_releases (e.g. 2026)."));
        props.put("after_release",  schemaProperty("string",  "Show entries after this release, copied from list_releases. Either a full release like \"2025u3\" (works across major versions, e.g. when upgrading from 2025 U3 to a 2026 release), or a bare U-number like \"2\" for U2 within major_version. Do NOT subtract or compute — copy the value directly."));
        props.put("after_date",     schemaProperty("string",  "Return entries after this date (ISO 8601 format, e.g. '2025-10-28'). Use for date-based queries like 'changes in the last month'."));
        props.put("after_build",    schemaProperty("integer", "Return entries after this build number. Only use if the user provides a specific build number. Prefer after_date or after_release instead."));
        return new McpSchema.JsonSchema("object", props,
                Arrays.asList("edition", "connector_name", "major_version"), null, null, null);
    }

    private static CallToolResult handleGetChangelog(Map<String, Object> args) {
        int majorVersion;
        Edition edition;
        try {
            majorVersion = requireMajorVersion(args);
            edition = requireEdition(args);
        } catch (IllegalArgumentException e) {
            return err(e.getMessage());
        }

        String connectorName = stringArg(args, "connector_name");
        if (connectorName == null) {
            return err("connector_name is required.");
        }

        String  afterRelease = stringArg(args, "after_release");
        String  afterDate    = stringArg(args, "after_date");
        Integer afterBuild   = optIntArg(args, "after_build");

        try {
            return ok(CLIENT.changelogReport(edition, majorVersion, connectorName,
                    afterRelease, afterDate, afterBuild));
        } catch (IllegalArgumentException e) {
            return err(appendConnectorHint(e.getMessage(), edition, majorVersion));
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return err("Error getting changelog: " + e.getMessage());
        }
    }

    // ============================================================
    //  MAIN
    // ============================================================

    public static void main(String[] args) throws Exception {
        // The transport owns stdin, so EOF (client gone) is observed here via a
        // wrapper; main then exits instead of outliving the disconnected client.
        CountDownLatch stdinClosed = new CountDownLatch(1);
        InputStream stdin = new FilterInputStream(System.in) {
            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b < 0) stdinClosed.countDown();
                return b;
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                int n = super.read(buf, off, len);
                if (n < 0) stdinClosed.countDown();
                return n;
            }
        };
        StdioServerTransportProvider transport =
                new StdioServerTransportProvider(McpJsonDefaults.getMapper(), stdin, System.out);

        McpServer.sync(transport)
                .serverInfo("cdata-changelog-review-mcp", version())
                .instructions("""
                        This server answers "what changed?" questions about CData driver connectors \
                        (Salesforce, MySQL, and ~280 others), which ship in several editions (JDBC, \
                        ADO .NET, ODBC, Python).

                        Domain model:
                        - A release is a major version year plus an update number: "2026 U0" is \
                        major_version 2026, update 0. Updates within a year are cumulative.
                        - Each major version has its own independent changelog per connector; pick \
                        major_version by which changelog the user wants to read, and a baseline \
                        (release, date, or build) for how far back to look. Cross-version questions \
                        ("what changed upgrading from 2025 U3 to 2026 U0?") use major_version 2026 \
                        with after_release "2025u3".
                        - A baseline release from an older major version is AMBIGUOUS unless the \
                        user said which changelog to read: "what changed since 2025 U2?" could mean \
                        the 2025 line's changes after U2 (major_version 2025) or everything in 2026 \
                        since that baseline (major_version 2026), and the two give different \
                        results. Ask the user which they mean before calling get_changelog.
                        - Build numbers are internal day counters; never compute or guess them. \
                        Prefer release or date baselines.

                        Workflow: list_releases first, then list_connectors if unsure of the exact \
                        connector name, then get_changelog. Never guess release numbers or connector \
                        names - only use values these tools returned. If the user gave no edition or \
                        no baseline, ask them instead of assuming.""")
                .capabilities(ServerCapabilities.builder().tools(true).build())

                .toolCall(
                        McpSchema.Tool.builder()
                                .name("list_releases")
                                .description(
                                        "List available CData connector releases, newest first, with the " +
                                        "get_changelog arguments for each. Call this before get_changelog - " +
                                        "only releases returned here are valid. No arguments required.")
                                .inputSchema(listReleasesSchema())
                                .build(),
                        (exchange, request) -> handleListReleases(
                                request.arguments() != null ? request.arguments() : Collections.emptyMap()))

                .toolCall(
                        McpSchema.Tool.builder()
                                .name("list_connectors")
                                .description(
                                        "List the valid connector names for an edition and major version. " +
                                        "Use a returned name verbatim as get_changelog's connector_name - " +
                                        "do not guess connector names.")
                                .inputSchema(listConnectorsSchema())
                                .build(),
                        (exchange, request) -> handleListConnectors(
                                request.arguments() != null ? request.arguments() : Collections.emptyMap()))

                .toolCall(
                        McpSchema.Tool.builder()
                                .name("get_changelog")
                                .description(
                                        "Get changelog entries for a CData connector since a baseline. " +
                                        "Requires EXACTLY ONE baseline: after_release, after_date, or after_build. " +
                                        "The major_version is NOT the current calendar year - use a value from " +
                                        "list_releases. If the baseline release is from a different major version " +
                                        "and the user did not say which changelog to read, ask them first. " +
                                        "Returns CSV with a header row.")
                                .inputSchema(getChangelogSchema())
                                .build(),
                        (exchange, request) -> handleGetChangelog(
                                request.arguments() != null ? request.arguments() : Collections.emptyMap()))

                .build();

        System.err.println("CData Changelog Review MCP server started.");
        stdinClosed.await();
        transport.closeGracefully().block(Duration.ofSeconds(5));
        System.exit(0);
    }
}
