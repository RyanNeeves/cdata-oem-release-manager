# embedded-drivers-tools

Tools for working with CData embedded / OEM drivers:

- **CData Release Manager (`cdrm`)** — a command-line tool to browse releases,
  review connector changelogs, and download driver builds (single connectors
  or entire editions).
- **CData Changelog Review** — an MCP server / Claude Code plugin that lets AI
  assistants answer questions like *"What changed in the Salesforce JDBC
  driver since 2025 U2?"*

## Prerequisites

- Java 17+

## CData Changelog Review (MCP server)

### Claude Code

No download required — install directly via the plugin marketplace:

```
/plugin marketplace add RyanNeeves/embedded-drivers-tools
/plugin install cdata-changelog-review@embedded-drivers-tools
/reload-plugins
```

**Example prompts:**

- "What CData connector releases are available?"
- "List all the changes made to the MongoDB connector since the second release of 2025"
- "What changed in the Salesforce ADO connector since build 25.0.9000?"
- "Were there any changes to the Snowflake connector last week?"

### Claude Desktop, Cursor, and other MCP clients

Download `cdata-changelog-review-mcp.jar` from the
[latest release](../../releases/latest), then add it to your client config:

- **Claude Desktop:** `%APPDATA%\Claude\claude_desktop_config.json` (Windows)
  or `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS)
- **Cursor:** `.cursor/mcp.json` in your project, or `~/.cursor/mcp.json` globally

```json
{
  "mcpServers": {
    "cdata-changelog-review": {
      "command": "/full/path/to/java",
      "args": ["-jar", "/absolute/path/to/cdata-changelog-review-mcp.jar"]
    }
  }
}
```

> **Tip:** MCP clients start the server without a shell, so use the full
> absolute path to your `java` binary if it isn't found automatically.

## CData Release Manager (CLI)

Download `cdrm.jar` from the [latest release](../../releases/latest).

```
# See what releases are available (e.g. 2025 U1, 2025 U2)
java -jar cdrm.jar releases

# List the connectors in an edition
java -jar cdrm.jar connectors --edition JDBC --major-version 2025

# What changed in a connector since a release, date, or build?
# (-v picks the changelog's major version; it defaults to the latest release's)
java -jar cdrm.jar changelog -e jdbc -c Salesforce --after-date 2025-10-28
java -jar cdrm.jar changelog -e jdbc -c Salesforce -v 2025 --after-release 2

# Upgrading across major versions? Baseline against the old version's release:
java -jar cdrm.jar changelog -e jdbc -c Salesforce -v 2026 --after-release 2025u2

# Download a driver build
java -jar cdrm.jar download -e jdbc -r latest -c Salesforce -o ./drivers

# Download several at once, or a whole edition (the default when no -c is given)
java -jar cdrm.jar download -e jdbc -r latest -c Salesforce -c MySQL -o ./drivers
java -jar cdrm.jar download -e python-windows -r latest -o ./drivers
```

**Editions:** `JDBC`, `ADO .NET FRAMEWORK`, `ADO .NET STANDARD`, `ODBC UNIX`,
`ODBC WINDOWS`, `PYTHON MAC`, `PYTHON UNIX`, `PYTHON WINDOWS`
(case doesn't matter, and spaces/dots/hyphens are interchangeable, so
`ado-net-framework` works unquoted in a shell).

**Releases:** `2025u3`, `v25u3`, `"2025 U3"`, or `latest`.

Run `java -jar cdrm.jar --help` or add `--help` to any command for details.

## Uninstall

### Claude Code

```
/plugin uninstall cdata-changelog-review@embedded-drivers-tools
/plugin marketplace remove embedded-drivers-tools
/reload-plugins
```

### Other MCP clients

Remove the `cdata-changelog-review` entry from your config file and delete
the JAR.

## Building from source

Requires Maven 3.9+. Run `mvn package` at the repo root; the jars land in
`cli/target/cdrm.jar` and `mcp/target/cdata-changelog-review-mcp.jar`. Both
run unmodified on Windows, Linux, and macOS.

## License

Licensed under the [MIT License](LICENSE).
