package com.cdata.releasemanager.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A CData driver edition. Each constant is one row of the bucket contract:
 * the constant itself is the edition's identity (and display name), declared
 * together with the bucket directory it lives in and the artifact filenames
 * it ships ({name} is the lowercase connector name).
 *
 * Connector names are always lowercase in the bucket, while wrapper text
 * around them keeps its fixed spelling (e.g. "System.Data.CData."). Callers
 * may supply any casing (a user may type "SAPConcur"); it is lowercased on
 * the way in.
 *
 * Build markers are named identically in every edition, so only their
 * location is an edition concern ({@link #markerPrefix(Release, String)});
 * parsing them lives in {@link BuildNumbers#fromMarker}.
 *
 * The bucket also carries two legacy editions (ado/net35 and odbc/net40/x86)
 * that are intentionally not modeled here.
 */
public enum Edition {
    JDBC("jdbc",
            "cdata.jdbc.{name}.jar"),
    ADO_NET_FRAMEWORK("ado/net40",
            "System.Data.CData.{name}.dll"),
    ADO_NET_STANDARD("ado/netstandard20",
            "System.Data.CData.{name}.dll"),
    ODBC_UNIX("odbc/linux/x64",
            "cdata.odbc.{name}.ini", "cdata.odbcm.{name}.jar", "lib{name}odbc.x64.so"),
    ODBC_WINDOWS("odbc/net40/x64",
            "CData.ODBC.{name}.dll", "CData.ODBCm.{name}.dll"),
    PYTHON_MAC("python/mac",
            "{name}.setup_mac.zip"),
    PYTHON_UNIX("python/unix",
            "{name}.setup_unix.zip"),
    PYTHON_WINDOWS("python/win",
            "{name}.setup_win.zip");

    private final String subpath;
    private final List<String> artifactPatterns;

    Edition(String subpath, String... artifactPatterns) {
        this.subpath = subpath;
        this.artifactPatterns = List.of(artifactPatterns);
    }

    /** Human-readable name: the constant with spaces, except ".NET" keeps its dot. */
    public String displayName() {
        return switch (this) {
            case ADO_NET_FRAMEWORK -> "ADO .NET FRAMEWORK";
            case ADO_NET_STANDARD  -> "ADO .NET STANDARD";
            default -> name().replace('_', ' ');
        };
    }

    /** All edition display names in declaration order, for help text and schemas. */
    public static List<String> displayNames() {
        List<String> names = new ArrayList<>();
        for (Edition e : values()) names.add(e.displayName());
        return names;
    }

    /** Bucket prefix for this edition's files in a release (e.g. "v26u0/ado/net40/"). */
    public String releasePrefix(Release release) {
        return release.tag() + "/" + subpath + "/";
    }

    /**
     * Bucket prefix matching exactly one connector's bld-* marker in a release
     * (e.g. "v26u0/jdbc/bld-salesforce."). The trailing dot keeps "salesforce"
     * from matching "salesforcemarketingcloud".
     */
    public String markerPrefix(Release release, String connectorName) {
        return releasePrefix(release) + BuildNumbers.MARKER_PREFIX
                + connectorName.toLowerCase(Locale.ROOT) + ".";
    }

    /** Bucket prefix for this edition's changelogs in a major version (e.g. "changelogs/v25/ado/"). */
    public String changelogPrefix(int majorVersion) {
        int slash = subpath.indexOf('/');
        String changelogDir = slash >= 0 ? subpath.substring(0, slash) : subpath;
        return "changelogs/v" + (majorVersion % 100) + "/" + changelogDir + "/";
    }

    /** Bucket key of a connector's changelog CSV (e.g. "changelogs/v25/ado/salesforce/changelog.csv"). */
    public String changelogKey(int majorVersion, String connectorName) {
        return changelogPrefix(majorVersion) + connectorName.toLowerCase(Locale.ROOT) + "/changelog.csv";
    }

    /**
     * The artifact filenames a connector has in this edition (multi-file
     * editions like ODBC UNIX have several).
     */
    public List<String> artifactFilenames(String connectorName) {
        String name = connectorName.toLowerCase(Locale.ROOT);
        return artifactPatterns.stream()
                .map(p -> p.replace("{name}", name))
                .toList();
    }

    /**
     * Parses user input leniently: case-insensitive, with spaces, dots, hyphens,
     * and underscores treated as equivalent (e.g. "ado-net-framework").
     */
    public static Edition parse(String input) {
        String normalized = normalize(input);
        for (Edition e : values()) {
            if (normalize(e.displayName()).equals(normalized) || e.name().equalsIgnoreCase(input.trim())) {
                return e;
            }
        }
        throw new IllegalArgumentException(
                "Unknown edition '" + input + "'. Valid editions: " + String.join(", ", displayNames()));
    }

    private static String normalize(String s) {
        return s.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s._-]+", " ");
    }
}
