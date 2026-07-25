package com.zephyr.croj.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcmScoreboardCalculatorTest {
    private static final Instant START = Instant.parse("2026-07-18T10:00:00Z");
    private static final Instant END = Instant.parse("2026-07-18T12:00:00Z");

    @Test
    void countsOnlyContestantErrorsBeforeFirstAccepted() {
        AcmScoreboardCalculator.Scoreboard board = AcmScoreboardCalculator.calculate(
                List.of(7L),
                List.of(new AcmScoreboardCalculator.Problem(42L, "A")),
                List.of(
                        fact(1, 7, 42, 3, 5),
                        fact(2, 7, 42, 7, 6),
                        fact(3, 7, 42, 0, 7),
                        fact(4, 7, 42, 1, 30),
                        fact(5, 7, 42, 3, 40)),
                START,
                END);

        AcmScoreboardCalculator.Row row = board.rows().get(0);
        assertEquals(1, row.solved());
        assertEquals(50, row.penaltyMinutes());
        assertEquals(1, row.problems().get(0).wrongAttempts());
    }

    @Test
    void marksOneDeterministicFirstAcceptedAndRanksByAcmRules() {
        AcmScoreboardCalculator.Scoreboard board = AcmScoreboardCalculator.calculate(
                List.of(7L, 8L, 9L),
                List.of(new AcmScoreboardCalculator.Problem(42L, "A")),
                List.of(
                        fact(10, 7, 42, 1, 20),
                        fact(9, 8, 42, 1, 20)),
                START,
                END);

        assertEquals(List.of(7L, 8L, 9L), board.rows().stream().map(AcmScoreboardCalculator.Row::userId).toList());
        assertFalse(board.rows().get(0).problems().get(0).firstAccepted());
        assertTrue(board.rows().get(1).problems().get(0).firstAccepted());
        assertEquals(List.of(1, 2, 3), board.rows().stream().map(AcmScoreboardCalculator.Row::rank).toList());
    }

    @Test
    void cutoffExcludesFrozenSubmissions() {
        Instant freeze = START.plusSeconds(30 * 60);
        AcmScoreboardCalculator.Scoreboard frozen = AcmScoreboardCalculator.calculate(
                List.of(7L),
                List.of(new AcmScoreboardCalculator.Problem(42L, "A")),
                List.of(fact(1, 7, 42, 1, 30)),
                START,
                freeze);
        AcmScoreboardCalculator.Scoreboard live = AcmScoreboardCalculator.calculate(
                List.of(7L),
                List.of(new AcmScoreboardCalculator.Problem(42L, "A")),
                List.of(fact(1, 7, 42, 1, 30)),
                START,
                END);

        assertEquals(0, frozen.rows().get(0).solved());
        assertEquals(1, live.rows().get(0).solved());
    }

    @Test
    void ignoresUnregisteredUsersAndNonContestProblemsWhenChoosingFirstAccepted() {
        AcmScoreboardCalculator.Scoreboard board = AcmScoreboardCalculator.calculate(
                List.of(7L),
                List.of(new AcmScoreboardCalculator.Problem(42L, "A")),
                List.of(
                        fact(1, 99, 42, 1, 1),
                        fact(2, 7, 999, 1, 2),
                        fact(3, 7, 42, 1, 3)),
                START,
                END);

        assertEquals(1, board.rows().get(0).solved());
        assertTrue(board.rows().get(0).problems().get(0).firstAccepted());
    }

    @Test
    void rejectsDuplicateParticipantAndProblemInputs() {
        assertThrows(IllegalArgumentException.class, () -> AcmScoreboardCalculator.calculate(
                List.of(7L, 7L), List.of(), List.of(), START, END));
        assertThrows(IllegalArgumentException.class, () -> AcmScoreboardCalculator.calculate(
                List.of(),
                List.of(
                        new AcmScoreboardCalculator.Problem(42L, "A"),
                        new AcmScoreboardCalculator.Problem(42L, "B")),
                List.of(),
                START,
                END));
    }

    private AcmScoreboardCalculator.SubmissionFact fact(
            long id, long userId, long problemId, int status, long minute) {
        return new AcmScoreboardCalculator.SubmissionFact(
                id, userId, problemId, status, START.plusSeconds(minute * 60));
    }
}
