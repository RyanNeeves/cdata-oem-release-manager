package com.cdata.embeddeddrivers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class EditionTest {

    @Test
    void derivesDisplayNames() {
        assertEquals("JDBC", Edition.JDBC.displayName());
        assertEquals("ADO .NET FRAMEWORK", Edition.ADO_NET_FRAMEWORK.displayName());
        assertEquals("ADO .NET STANDARD", Edition.ADO_NET_STANDARD.displayName());
        assertEquals("ODBC UNIX", Edition.ODBC_UNIX.displayName());
        assertEquals("PYTHON WINDOWS", Edition.PYTHON_WINDOWS.displayName());
    }

    @Test
    void parsesCanonicalNames() {
        assertEquals(Edition.JDBC, Edition.parse("JDBC"));
        assertEquals(Edition.ADO_NET_FRAMEWORK, Edition.parse("ADO .NET FRAMEWORK"));
    }

    @Test
    void parsesLenientVariants() {
        assertEquals(Edition.JDBC, Edition.parse("jdbc"));
        assertEquals(Edition.ADO_NET_FRAMEWORK, Edition.parse("ado-net-framework"));
        assertEquals(Edition.ODBC_WINDOWS, Edition.parse("odbc_windows"));
        assertEquals(Edition.PYTHON_MAC, Edition.parse("python mac"));
    }

    @Test
    void rejectsUnknownEdition() {
        assertThrows(IllegalArgumentException.class, () -> Edition.parse("COBOL"));
    }

    @Test
    void buildsBucketPrefixes() {
        Release r = new Release(2026, 0);
        assertEquals("v26u0/jdbc/", Edition.JDBC.releasePrefix(r));
        assertEquals("v26u0/ado/net40/", Edition.ADO_NET_FRAMEWORK.releasePrefix(r));
        // trailing dot so "salesforce" cannot match "salesforcemarketingcloud"
        assertEquals("v26u0/jdbc/bld-salesforce.", Edition.JDBC.markerPrefix(r, "Salesforce"));
        assertEquals("v26u0/ado/net40/bld-sapconcur.", Edition.ADO_NET_FRAMEWORK.markerPrefix(r, "SAPConcur"));
        assertEquals("changelogs/v25/jdbc/", Edition.JDBC.changelogPrefix(2025));
        assertEquals("changelogs/v25/odbc/", Edition.ODBC_UNIX.changelogPrefix(2025));
        assertEquals("changelogs/v25/ado/salesforce/changelog.csv",
                Edition.ADO_NET_FRAMEWORK.changelogKey(2025, "Salesforce"));
    }

    @Test
    void constructsArtifactFilenames() {
        // caller-supplied connector names are lowercased into the fixed template
        assertEquals(List.of("cdata.jdbc.salesforce.jar"),
                Edition.JDBC.artifactFilenames("Salesforce"));
        assertEquals(List.of("System.Data.CData.sapconcur.dll"),
                Edition.ADO_NET_FRAMEWORK.artifactFilenames("SAPConcur"));
        assertEquals(List.of("cdata.odbc.salesforce.ini", "cdata.odbcm.salesforce.jar", "libsalesforceodbc.x64.so"),
                Edition.ODBC_UNIX.artifactFilenames("salesforce"));
        assertEquals(List.of("CData.ODBC.salesforce.dll", "CData.ODBCm.salesforce.dll"),
                Edition.ODBC_WINDOWS.artifactFilenames("salesforce"));
        assertEquals(List.of("salesforce.setup_mac.zip"),
                Edition.PYTHON_MAC.artifactFilenames("salesforce"));
    }
}
