package com.zephyr.croj.contest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OiScoreboardCalculatorTest {
    private static final Instant START = Instant.parse("2026-07-10T10:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-07-10T12:00:00Z");

    @Test
    void ranksByBestPerProblemScoreThenEarlierLastImprovement() {
        var board = OiScoreboardCalculator.calculate(
                List.of(7L, 8L, 9L),
                List.of(
                        new OiScoreboardCalculator.Problem(41L, "A", 100),
                        new OiScoreboardCalculator.Problem(42L, "B", 50)),
                List.of(
                        fact(1, 7, 41, 60, "10:10:00"),
                        fact(2, 7, 41, 100, "10:30:00"),
                        fact(3, 7, 42, 20, "10:40:00"),
                        fact(4, 8, 41, 70, "10:20:00"),
                        fact(5, 8, 42, 50, "10:35:00"),
                        fact(6, 9, 41, 100, "10:05:00")),
                START,
                CUTOFF);

        assertEquals(List.of(8L, 7L, 9L), board.rows().stream()
                .map(OiScoreboardCalculator.Row::userId)
                .toList());
        assertEquals(120, board.rows().get(0).totalScore());
        assertEquals(2, board.rows().get(0).scoredProblems());
        assertEquals(120, board.rows().get(1).totalScore());
        assertEquals(100, board.rows().get(2).totalScore());
        assertEquals(100, board.rows().get(1).problems().get(0).score());
        assertEquals(2L, board.rows().get(1).problems().get(0).submissionId());
    }

    @Test
    void ignoresOutOfWindowUnknownAndScorelessSubmissions() {
        var board = OiScoreboardCalculator.calculate(
                List.of(7L),
                List.of(new OiScoreboardCalculator.Problem(41L, "A", 100)),
                List.of(
                        factAt(1, 7, 41, 100, START.minusSeconds(1)),
                        factAt(2, 8, 41, 100, START.plusSeconds(1)),
                        factAt(3, 7, 99, 100, START.plusSeconds(1)),
                        factAt(4, 7, 41, null, START.plusSeconds(2)),
                        factAt(6, 7, 41, 0, START.plusSeconds(3)),
                        factAt(5, 7, 41, 80, CUTOFF)),
                START,
                CUTOFF);

        var row = board.rows().get(0);
        assertEquals(0, row.totalScore());
        assertEquals(0, row.scoredProblems());
        assertNull(row.lastImprovedAt());
        assertEquals(6L, row.problems().get(0).submissionId());
    }

    @Test
    void rejectsScoresOutsideThePinnedContestProblemMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OiScoreboardCalculator.calculate(
                        List.of(7L),
                        List.of(new OiScoreboardCalculator.Problem(41L, "A", 100)),
                        List.of(fact(1, 7, 41, 101, "10:10:00")),
                        START,
                        CUTOFF));
    }

    @Test
    void equalBestScoresKeepTheEarlierTimeThenLowerSubmissionId() {
        Instant sameTime = START.plusSeconds(60);
        var board = OiScoreboardCalculator.calculate(
                List.of(7L),
                List.of(new OiScoreboardCalculator.Problem(41L, "A", 100)),
                List.of(
                        factAt(9, 7, 41, 80, sameTime),
                        factAt(7, 7, 41, 80, sameTime),
                        factAt(6, 7, 41, 80, sameTime.plusSeconds(1))),
                START,
                CUTOFF);

        assertEquals(7L, board.rows().get(0).problems().get(0).submissionId());
    }

    private OiScoreboardCalculator.SubmissionFact fact(
            long id, long userId, long problemId, Integer score, String time) {
        return factAt(id, userId, problemId, score, Instant.parse("2026-07-10T" + time + "Z"));
    }

    private OiScoreboardCalculator.SubmissionFact factAt(
            long id, long userId, long problemId, Integer score, Instant time) {
        return new OiScoreboardCalculator.SubmissionFact(id, userId, problemId, score, time);
    }
}
