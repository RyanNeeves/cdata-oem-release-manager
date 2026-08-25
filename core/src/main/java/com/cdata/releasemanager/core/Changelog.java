package com.cdata.releasemanager.core;

import java.util.ArrayList;
import java.util.List;

/** Parses and filters changelog CSV content. */
public final class Changelog {

    private Changelog() {
    }

    /** Header line plus the entry lines newer than a baseline build. */
    public record Filtered(String header, List<String> entries) {
    }

    /**
     * Filters changelog CSV to entries whose Version build number is greater
     * than {@code baselineBuild}.
     *
     * @throws IllegalArgumentException if the CSV has no 'Version' column
     */
    public static Filtered filterAfterBuild(String csvBody, int baselineBuild) {
        String[] lines = csvBody.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        if (lines.length < 2) {
            return new Filtered(lines.length > 0 ? lines[0] : "", List.of());
        }

        int versionCol = columnIndex(lines[0], "Version");
        if (versionCol < 0) {
            throw new IllegalArgumentException("Changelog CSV missing 'Version' column.");
        }

        List<String> filtered = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] fields = splitLine(line);
            if (versionCol < fields.length && BuildNumbers.fromVersion(fields[versionCol]) > baselineBuild) {
                filtered.add(line);
            }
        }
        return new Filtered(lines[0], filtered);
    }

    /** Finds the index of a column name in a CSV header line. Returns -1 if not found. */
    static int columnIndex(String headerLine, String columnName) {
        String[] fields = splitLine(headerLine);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].trim().equals(columnName)) return i;
        }
        return -1;
    }

    /** Splits a CSV line respecting quoted fields and escaped double-quotes. */
    static String[] splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        char[] chars = line.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '"') {
                if (inQuotes && i + 1 < chars.length && chars[i + 1] == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString());
                cur = new StringBuilder();
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }
}
