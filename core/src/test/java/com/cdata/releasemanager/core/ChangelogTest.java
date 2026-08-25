package com.cdata.releasemanager.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChangelogTest {

    private static final String CSV = String.join("\r\n",
            "Date,Version,Notes",
            "2025-01-10,25.0.9000,\"Fixed a bug, and another\"",
            "2025-03-01,25.0.9434,Improved things",
            "2025-06-15,25.0.9500,New feature");

    @Test
    void filtersEntriesAfterBaseline() {
        Changelog.Filtered result = Changelog.filterAfterBuild(CSV, 9434);
        assertEquals("Date,Version,Notes", result.header());
        assertEquals(1, result.entries().size());
        assertTrue(result.entries().get(0).contains("25.0.9500"));
    }

    @Test
    void returnsAllEntriesForLowBaseline() {
        assertEquals(3, Changelog.filterAfterBuild(CSV, 1).entries().size());
    }

    @Test
    void returnsEmptyForHighBaseline() {
        assertTrue(Changelog.filterAfterBuild(CSV, 99999).entries().isEmpty());
    }

    @Test
    void rejectsMissingVersionColumn() {
        assertThrows(IllegalArgumentException.class,
                () -> Changelog.filterAfterBuild("Date,Notes\n2025-01-10,x", 1));
    }

    @Test
    void splitsCsvLines() {
        assertArrayEquals(new String[] {"a", "b", "c"}, Changelog.splitLine("a,b,c"));
        assertArrayEquals(new String[] {"a", "b,c", "d"}, Changelog.splitLine("a,\"b,c\",d"));
        assertArrayEquals(new String[] {"say \"hi\"", "x"}, Changelog.splitLine("\"say \"\"hi\"\"\",x"));
    }

    @Test
    void findsColumnIndex() {
        assertEquals(1, Changelog.columnIndex("Date,Version,Notes", "Version"));
        assertEquals(-1, Changelog.columnIndex("Date,Notes", "Version"));
    }
}
