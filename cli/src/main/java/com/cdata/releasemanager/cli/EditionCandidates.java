package com.cdata.releasemanager.cli;

import java.util.Iterator;

import com.cdata.releasemanager.core.Edition;

/**
 * Supplies edition display names to picocli's ${COMPLETION-CANDIDATES}, so
 * --edition help text is derived from the enum instead of hand-maintained.
 */
public class EditionCandidates implements Iterable<String> {

    @Override
    public Iterator<String> iterator() {
        return Edition.displayNames().iterator();
    }
}
