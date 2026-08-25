package com.cdata.releasemanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OemBuildsClientTest {

    /**
     * Serves canned responses instead of hitting the bucket: URL-substring
     * routes first, then the default body, then 404.
     */
    private static class StubReader extends BucketReader {
        private final Map<String, String> routes = new LinkedHashMap<>();
        private final String defaultBody;
        String lastUrl;

        StubReader() {
            this(null);
        }

        StubReader(String defaultBody) {
            super(OemBuildsClient.BASE_URL);
            this.defaultBody = defaultBody;
        }

        StubReader route(String urlPart, String body) {
            routes.put(urlPart, body);
            return this;
        }

        @Override
        HttpResult get(String url) {
            lastUrl = url;
            for (Map.Entry<String, String> route : routes.entrySet()) {
                if (url.contains(route.getKey())) return new HttpResult(200, route.getValue());
            }
            return defaultBody != null ? new HttpResult(200, defaultBody) : new HttpResult(404, "");
        }
    }

    private static String listing(String... keys) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><ListBucketResult>");
        for (String k : keys) {
            sb.append("<Contents><Key>").append(k).append("</Key><Size>10</Size></Contents>");
        }
        return sb.append("<IsTruncated>false</IsTruncated></ListBucketResult>").toString();
    }

    private static String prefixListing(String... prefixes) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><ListBucketResult>");
        for (String p : prefixes) {
            sb.append("<CommonPrefixes><Prefix>").append(p).append("</Prefix></CommonPrefixes>");
        }
        return sb.append("<IsTruncated>false</IsTruncated></ListBucketResult>").toString();
    }

    @Test
    void listDriverFilesDropsBuildMarkers() throws IOException {
        OemBuildsClient client = new OemBuildsClient(new StubReader(listing(
                "v26u0/odbc/linux/x64/bld-aas.9655",
                "v26u0/odbc/linux/x64/cdata.odbc.aas.ini",
                "v26u0/odbc/linux/x64/cdata.odbcm.aas.jar",
                "v26u0/odbc/linux/x64/libaasodbc.x64.so")));

        List<RemoteFile> files = client.listDriverFiles(new Release(2026, 0), Edition.ODBC_UNIX);

        assertEquals(List.of("cdata.odbc.aas.ini", "cdata.odbcm.aas.jar", "libaasodbc.x64.so"),
                files.stream().map(RemoteFile::filename).toList());
    }

    @Test
    void releaseToBuildNumberQueriesOneMarker() throws IOException {
        StubReader reader = new StubReader(listing("v26u0/jdbc/bld-salesforce.9666"));
        OemBuildsClient client = new OemBuildsClient(reader);

        assertEquals(9666, client.releaseToBuildNumber(new Release(2026, 0), Edition.JDBC, "Salesforce"));
        // narrowed to the single connector, with the trailing dot that stops
        // "salesforce" from also matching "salesforcepardot"
        assertTrue(reader.lastUrl.contains("prefix=v26u0%2Fjdbc%2Fbld-salesforce."), reader.lastUrl);
    }

    @Test
    void listReleasesParsesTags() throws IOException {
        OemBuildsClient client = new OemBuildsClient(new StubReader(
                prefixListing("v25u2/", "v26u0/", "not-a-release/")));

        List<Release> releases = client.listReleases();

        // newest first, non-release prefixes skipped
        assertEquals(List.of(new Release(2026, 0), new Release(2025, 2)), releases);
    }

    @Test
    void listConnectorsStripsPrefixAndSorts() throws IOException {
        OemBuildsClient client = new OemBuildsClient(new StubReader(prefixListing(
                "changelogs/v26/ado/salesforce/",
                "changelogs/v26/ado/aas/")));

        assertEquals(List.of("aas", "salesforce"),
                client.listConnectors(Edition.ADO_NET_FRAMEWORK, 2026));
    }

    @Test
    void listConnectorsRejectsUnknownMajorVersion() {
        OemBuildsClient client = new OemBuildsClient(new StubReader()
                .route("changelogs", prefixListing())
                .route("prefix=v&", prefixListing("v26u0/")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> client.listConnectors(Edition.JDBC, 2099));
        assertTrue(e.getMessage().contains("2099 does not exist"), e.getMessage());
    }

    @Test
    void changelogReportRendersFilteredEntries() throws IOException {
        OemBuildsClient client = new OemBuildsClient(new StubReader()
                .route("bld-salesforce.", listing("v26u0/jdbc/bld-salesforce.9434"))
                .route("changelogs/v26/jdbc/salesforce/changelog.csv",
                        "Date,Version,Notes\n2026-01-10,26.0.9400,Old fix\n2026-03-01,26.0.9500,New fix"));

        String report = client.changelogReport(Edition.JDBC, 2026, "Salesforce", "0", null, null);

        assertTrue(report.startsWith("Changelog: Salesforce (JDBC) v2026 - 1 entry after build 9434"), report);
        assertTrue(report.contains("26.0.9500"));
        assertFalse(report.contains("26.0.9400"));
    }

    @Test
    void changelogReportRequiresExactlyOneBaseline() {
        OemBuildsClient client = new OemBuildsClient(new StubReader());

        assertThrows(IllegalArgumentException.class,
                () -> client.changelogReport(Edition.JDBC, 2026, "salesforce", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> client.changelogReport(Edition.JDBC, 2026, "salesforce", "2", "2026-01-01", null));
    }
}
