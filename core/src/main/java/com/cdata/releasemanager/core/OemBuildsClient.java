package com.cdata.releasemanager.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Client for the public CData OEM builds bucket. Discovers releases,
 * connectors, build markers, and changelogs, and downloads driver builds.
 * Transport lives in {@link BucketReader}; this class holds only the
 * bucket contract's semantics.
 */
public class OemBuildsClient {

    public static final String BASE_URL = "https://downloads.cdata.com/cdataoembuilds";

    private final BucketReader bucket;

    public OemBuildsClient() {
        this(new BucketReader(BASE_URL));
    }

    OemBuildsClient(BucketReader bucket) {
        this.bucket = bucket;
    }

    /** Discovers all available releases from S3 prefixes, newest first. */
    public List<Release> listReleases() throws IOException {
        Set<Release> releases = new TreeSet<>();
        for (String prefix : bucket.listPrefixes("v")) {
            try {
                releases.add(Release.parse(prefix.substring(0, prefix.length() - 1)));
            } catch (IllegalArgumentException e) {
                // A v* directory that isn't a release tag - not ours to list.
            }
        }
        return new ArrayList<>(releases);
    }

    /** The newest available release. */
    public Release latestRelease() throws IOException {
        List<Release> releases = listReleases();
        if (releases.isEmpty()) throw new IOException("No releases found in bucket.");
        return releases.get(0);
    }

    /** Whether any release exists for the given major version year. */
    private boolean majorVersionExists(int year) throws IOException {
        for (Release r : listReleases()) {
            if (r.year() == year) return true;
        }
        return false;
    }

    /** Validates that a release exists in the bucket (or is a known hardcoded release). */
    public void requireRelease(Release release) throws IOException {
        List<Release> releases = listReleases();
        if (!releases.contains(release)) {
            StringBuilder sb = new StringBuilder("Release '" + release.label() + "' does not exist. Available releases:");
            for (Release r : releases) sb.append("\n  ").append(r.label());
            throw new IllegalArgumentException(sb.toString());
        }
    }

    /**
     * Lists the connector names that have changelogs for an edition and major
     * version, sorted. An empty list means the major version exists but has no
     * connectors for this edition; a major version that does not exist at all
     * throws IllegalArgumentException.
     */
    public List<String> listConnectors(Edition edition, int majorVersion) throws IOException {
        String clPrefix = edition.changelogPrefix(majorVersion);
        Set<String> names = new TreeSet<>();
        for (String prefix : bucket.listPrefixes(clPrefix)) {
            names.add(prefix.substring(clPrefix.length(), prefix.length() - 1));
        }
        if (names.isEmpty() && !majorVersionExists(majorVersion)) {
            throw new IllegalArgumentException("Major version " + majorVersion + " does not exist.");
        }
        return new ArrayList<>(names);
    }

    /**
     * Resolves a release to its build number for a connector via S3 build
     * marker lookup. Each connector has exactly one marker per release, so
     * the listing is prefixed down to that marker.
     */
    int releaseToBuildNumber(Release release, Edition edition, String connectorName)
            throws IOException {
        for (RemoteFile f : bucket.listFiles(edition.markerPrefix(release, connectorName))) {
            int build = BuildNumbers.fromMarker(f.filename(), connectorName);
            if (build >= 0) return build;
        }

        // No marker found: distinguish an invalid release from an unknown connector.
        requireRelease(release);
        throw new IllegalArgumentException(
                "No build found for '" + connectorName + "' in " + edition.displayName() + " / " + release.tag() + ".");
    }

    /**
     * The full changelog query shared by every frontend: resolves the baseline
     * from exactly one of a release, an ISO date, or an explicit build number,
     * fetches and filters the connector's changelog, and renders the report.
     * Invalid input and unknown connectors/releases throw
     * IllegalArgumentException with a user-facing message.
     */
    public String changelogReport(Edition edition, int majorVersion, String connectorName,
            String afterRelease, String afterDate, Integer afterBuild) throws IOException {
        int baselineBuild = resolveBaseline(edition, majorVersion, connectorName, afterRelease, afterDate, afterBuild);

        String csv = fetchChangelogCsv(edition, majorVersion, connectorName);
        if (csv == null) {
            throw new IllegalArgumentException(
                    "No changelog found for '" + connectorName + "' (" + edition.displayName() + ").");
        }

        Changelog.Filtered result = Changelog.filterAfterBuild(csv, baselineBuild);
        if (result.entries().isEmpty()) {
            return "No changelog entries after build " + baselineBuild + " for '" + connectorName
                    + "' in major version " + majorVersion + ". The connector is unchanged since that baseline.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Changelog: %s (%s) v%d - %d entr%s after build %d%n%n",
                connectorName, edition.displayName(), majorVersion,
                result.entries().size(), result.entries().size() == 1 ? "y" : "ies", baselineBuild));
        sb.append(result.header()).append('\n');
        for (String line : result.entries()) {
            sb.append(line).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Resolves a changelog baseline build number from exactly one of a
     * release, an ISO date, or an explicit build number. The release may be a
     * bare U-number within {@code majorVersion} or a full release in any major
     * version (e.g. "2025u3") - build numbers are days since 2000-01-01, so
     * they are comparable across major versions.
     */
    private int resolveBaseline(Edition edition, int majorVersion, String connectorName,
            String afterRelease, String afterDate, Integer afterBuild) throws IOException {
        int given = (afterRelease != null ? 1 : 0) + (afterDate != null ? 1 : 0) + (afterBuild != null ? 1 : 0);
        if (given == 0) {
            throw new IllegalArgumentException("No baseline given: provide a release number, date, or build number.");
        }
        if (given > 1) {
            throw new IllegalArgumentException("Provide only one baseline: a release number, date, or build number.");
        }
        if (afterDate != null) {
            return BuildNumbers.fromDate(afterDate);
        }
        if (afterBuild != null) {
            if (afterBuild < 1) throw new IllegalArgumentException("The build number must be positive.");
            return afterBuild;
        }
        return releaseToBuildNumber(Release.parseOrNumber(afterRelease, majorVersion), edition, connectorName);
    }

    /**
     * Fetches the raw changelog CSV for a connector. Returns null if the
     * changelog does not exist (HTTP 404).
     */
    private String fetchChangelogCsv(Edition edition, int majorVersion, String connectorName) throws IOException {
        BucketReader.HttpResult res = bucket.getObject(edition.changelogKey(majorVersion, connectorName));
        if (res.status() == 404) return null;
        if (res.status() != 200) {
            throw new IOException("HTTP " + res.status() + " fetching changelog for '" + connectorName + "'.");
        }
        return res.body();
    }

    /**
     * Lists the downloadable driver artifacts for a release and edition. A
     * release prefix holds exactly the artifacts plus one bld-* marker per
     * connector, so dropping the markers leaves the artifacts.
     */
    public List<RemoteFile> listDriverFiles(Release release, Edition edition) throws IOException {
        List<RemoteFile> artifacts = new ArrayList<>();
        for (RemoteFile f : bucket.listFiles(edition.releasePrefix(release))) {
            if (!f.filename().startsWith(BuildNumbers.MARKER_PREFIX)) artifacts.add(f);
        }
        return artifacts;
    }

    /** Downloads a driver artifact to {@code destDir}. See {@link BucketReader#download}. */
    public Path download(RemoteFile file, Path destDir) throws IOException {
        return bucket.download(file, destDir);
    }
}
