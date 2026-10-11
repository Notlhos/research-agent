package io.github.notlhos.research.core.persistence;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a research run: {@code PENDING}, then one active step at a time, then a terminal status.
 */
public enum RunStatus {
    PENDING,
    PLANNING,
    SEARCHING,
    INDEXING,
    RETRIEVING,
    WRITING,
    DONE,
    FAILED,
    CANCELLED;

    /**
     * Steps during which the run holds one of the {@code research.max-active-runs} slots.
     */
    public static final Set<RunStatus> ACTIVE = EnumSet.of(PLANNING, SEARCHING, INDEXING, RETRIEVING, WRITING);

    public boolean isTerminal() {
        return this == DONE || this == FAILED || this == CANCELLED;
    }

}
