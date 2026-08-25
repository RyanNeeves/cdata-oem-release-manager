package com.cdata.releasemanager.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A CData connector release identified by major version year and U-number (e.g. 2025 U2). */
public record Release(int year, int releaseNumber) implements Comparable<Release> {

    private static final Pattern PAT_INPUT = Pattern.compile("(?i)^v?(\\d{2}|\\d{4})[\\s._-]*u(\\d+)$");

    /** Bucket prefix tag, e.g. "v25u2". */
    public String tag() {
        return String.format("v%du%d", year % 100, releaseNumber);
    }

    /** Human-readable label, e.g. "2025 U2". */
    public String label() {
        return String.format("%d U%d", year, releaseNumber);
    }

    /** Newest first. */
    @Override
    public int compareTo(Release other) {
        if (year != other.year) return Integer.compare(other.year, year);
        return Integer.compare(other.releaseNumber, releaseNumber);
    }

    /** Parses lenient user input: "2025u3", "v25u3", "2025 U3", "2025-u3". */
    public static Release parse(String input) {
        Matcher m = PAT_INPUT.matcher(input.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Invalid release '" + input + "'. Expected a form like '2025u3' or 'v25u3'.");
        }
        int year = Integer.parseInt(m.group(1));
        if (year < 100) year += 2000;
        return new Release(year, Integer.parseInt(m.group(2)));
    }

    /**
     * Parses either a bare U-number, meaning a release within
     * {@code defaultYear} (e.g. "2" for U2), or a full release in any major
     * version like "2025u3".
     */
    public static Release parseOrNumber(String input, int defaultYear) {
        String s = input.trim();
        int u;
        try {
            u = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return parse(s);
        }
        if (u < 0) {
            throw new IllegalArgumentException(
                    "The release number must be >= 0 (the U-number, e.g. 2 for U2).");
        }
        return new Release(defaultYear, u);
    }
}
