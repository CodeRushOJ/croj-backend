package com.zephyr.croj.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContestPolicyTest {
    private static final Instant OPEN = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-07-09T00:00:00Z");
    private static final Instant START = Instant.parse("2026-07-10T00:00:00Z");
    private static final Instant FREEZE = Instant.parse("2026-07-10T01:30:00Z");
    private static final Instant END = Instant.parse("2026-07-10T02:00:00Z");

    @Test
    void derivesEveryExternalPhaseFromLifecycleAndServerTime() {
        assertEquals(ContestPhase.DRAFT, phase("DRAFT", OPEN));
        assertEquals(ContestPhase.CANCELLED, phase("CANCELLED", START));
        assertEquals(ContestPhase.REGISTRATION, phase("PUBLISHED", OPEN));
        assertEquals(ContestPhase.SCHEDULED, phase("PUBLISHED", CLOSE));
        assertEquals(ContestPhase.RUNNING, phase("PUBLISHED", START));
        assertEquals(ContestPhase.FROZEN, phase("PUBLISHED", FREEZE));
        assertEquals(ContestPhase.ENDED, phase("PUBLISHED", END));
    }

    @Test
    void rejectsInvalidRegistrationAndFreezeWindows() {
        assertThrows(IllegalArgumentException.class, () ->
                ContestPolicy.validateSchedule(CLOSE, OPEN, START, FREEZE, END));
        assertThrows(IllegalArgumentException.class, () ->
                ContestPolicy.validateSchedule(OPEN, CLOSE, START, START, END));
        assertThrows(IllegalArgumentException.class, () ->
                ContestPolicy.validateSchedule(OPEN, CLOSE, END, null, START));
    }

    @Test
    void preventsPreStartScoreboardAndProblemLeaks() {
        assertFalse(ContestPolicy.canReadScoreboard(ContestPhase.REGISTRATION));
        assertFalse(ContestPolicy.canReadScoreboard(ContestPhase.SCHEDULED));
        assertTrue(ContestPolicy.canReadScoreboard(ContestPhase.RUNNING));
        assertTrue(ContestPolicy.canReadScoreboard(ContestPhase.FROZEN));
        assertTrue(ContestPolicy.canReadScoreboard(ContestPhase.ENDED));

        assertFalse(ContestPolicy.canReadProblems("PUBLIC", ContestPhase.SCHEDULED, false, false));
        assertTrue(ContestPolicy.canReadProblems("PUBLIC", ContestPhase.RUNNING, true, false));
        assertFalse(ContestPolicy.canReadProblems("PUBLIC", ContestPhase.RUNNING, false, false));
        assertTrue(ContestPolicy.canReadProblems("PUBLIC", ContestPhase.ENDED, false, false));
        assertTrue(ContestPolicy.canReadProblems("PRIVATE", ContestPhase.SCHEDULED, false, true));
    }

    @Test
    void publicRegistrationIsSelfServiceButPrivateRegistrationIsManaged() {
        assertTrue(ContestPolicy.canSelfRegister("PUBLIC", ContestPhase.REGISTRATION));
        assertFalse(ContestPolicy.canSelfRegister("PRIVATE", ContestPhase.REGISTRATION));
        assertFalse(ContestPolicy.canSelfRegister("PUBLIC", ContestPhase.SCHEDULED));
    }

    private ContestPhase phase(String lifecycle, Instant now) {
        return ContestPolicy.phase(lifecycle, OPEN, CLOSE, START, FREEZE, END, now);
    }
}
