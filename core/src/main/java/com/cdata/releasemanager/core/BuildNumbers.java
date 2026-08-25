package com.cdata.releasemanager.core;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Conversions to CData build numbers (days since 2000-01-01 UTC). */
public final class BuildNumbers {

    /** Prefix of build marker filenames, identical in every edition. */
    public static final String MARKER_PREFIX = "bld-";

    private static final LocalDate EPOCH_2000 = LocalDate.of(2000, 1, 1);

    private BuildNumbers() {
    }

    /**
     * Extracts the build number from a bld-* marker filename (e.g.
     * "bld-salesforce.9655" -> 9655) if the marker belongs to the given
     * connector, comparing names case-insensitively. Returns -1 otherwise.
     * Marker naming is identical across editions, so the edition is irrelevant
     * here - only {@code Edition.markerPrefix} knows where markers live.
     */
    public static int fromMarker(String filename, String connectorName) {
        if (!filename.regionMatches(true, 0, MARKER_PREFIX, 0, MARKER_PREFIX.length())) return -1;
        String rest = filename.substring(MARKER_PREFIX.length());
        int dot = rest.lastIndexOf('.');
        if (dot <= 0 || dot == rest.length() - 1) return -1;
        if (!rest.substring(0, dot).equalsIgnoreCase(connectorName)) return -1;
        try {
            return Integer.parseInt(rest.substring(dot + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Extracts the build number from a changelog version string (e.g. "25.0.9434" -> 9434). Returns -1 on failure. */
    public static int fromVersion(String versionStr) {
        String[] parts = versionStr.trim().split("\\.");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[parts.length - 1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    /** Converts an ISO 8601 date (e.g. "2025-10-28") to a build number. */
    public static int fromDate(String iso) {
        LocalDate date;
        try {
            date = LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date, expected YYYY-MM-DD: " + iso);
        }
        if (date.getYear() < 2000 || date.getYear() > 2100) {
            throw new IllegalArgumentException("Year out of range in date: " + iso);
        }
        return (int) ChronoUnit.DAYS.between(EPOCH_2000, date);
    }
}
