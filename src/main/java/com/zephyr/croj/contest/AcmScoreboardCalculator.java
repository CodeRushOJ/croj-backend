package com.zephyr.croj.contest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class AcmScoreboardCalculator {
    private AcmScoreboardCalculator() {}

    public record Problem(long problemId, String label) {}

    public record SubmissionFact(
            long submissionId, long userId, long problemId, int status, Instant submittedAt) {}

    public record ProblemScore(
            long problemId,
            String label,
            boolean accepted,
            int wrongAttempts,
            int penaltyMinutes,
            Instant acceptedAt,
            boolean firstAccepted) {}

    public record Row(
            int rank,
            long userId,
            int solved,
            int penaltyMinutes,
            Instant lastAcceptedAt,
            List<ProblemScore> problems) {}

    public record Scoreboard(List<Row> rows) {}

    public static Scoreboard calculate(
            List<Long> participantIds,
            List<Problem> problems,
            List<SubmissionFact> submissions,
            Instant startsAt,
            Instant cutoffAt) {
        if (participantIds == null || problems == null || submissions == null || startsAt == null || cutoffAt == null) {
            throw new IllegalArgumentException("scoreboard inputs are required");
        }
        var participantSet = new HashSet<>(participantIds);
        var problemIds = new HashSet<Long>();
        var problemLabels = new HashSet<String>();
        for (Problem problem : problems) {
            problemIds.add(problem.problemId());
            problemLabels.add(problem.label());
        }
        if (participantSet.size() != participantIds.size()
                || problemIds.size() != problems.size()
                || problemLabels.size() != problems.size()) {
            throw new IllegalArgumentException("participants, problem IDs, and problem labels must be unique");
        }
        List<SubmissionFact> facts = submissions.stream()
                .filter(fact -> !fact.submittedAt().isBefore(startsAt))
                .filter(fact -> fact.submittedAt().isBefore(cutoffAt))
                .filter(fact -> participantSet.contains(fact.userId()))
                .filter(fact -> problemIds.contains(fact.problemId()))
                .sorted(Comparator.comparing(SubmissionFact::submittedAt)
                        .thenComparingLong(SubmissionFact::submissionId))
                .toList();
        Map<Long, Long> firstAcceptedByProblem = new HashMap<>();
        for (SubmissionFact fact : facts) {
            if (fact.status() == 1) {
                firstAcceptedByProblem.putIfAbsent(fact.problemId(), fact.submissionId());
            }
        }

        Map<UserProblem, List<SubmissionFact>> grouped = new HashMap<>();
        for (SubmissionFact fact : facts) {
            grouped.computeIfAbsent(new UserProblem(fact.userId(), fact.problemId()), ignored -> new ArrayList<>())
                    .add(fact);
        }

        List<Row> unordered = new ArrayList<>();
        for (Long userId : participantIds) {
            int solved = 0;
            int totalPenalty = 0;
            Instant lastAcceptedAt = null;
            List<ProblemScore> problemScores = new ArrayList<>();
            for (Problem problem : problems) {
                int wrongAttempts = 0;
                SubmissionFact accepted = null;
                for (SubmissionFact fact : grouped.getOrDefault(
                        new UserProblem(userId, problem.problemId()), List.of())) {
                    if (fact.status() == 1) {
                        accepted = fact;
                        break;
                    }
                    if (fact.status() >= 2 && fact.status() <= 6) {
                        wrongAttempts++;
                    }
                }
                int problemPenalty = 0;
                if (accepted != null) {
                    solved++;
                    problemPenalty = Math.toIntExact(Duration.between(startsAt, accepted.submittedAt()).toMinutes())
                            + wrongAttempts * 20;
                    totalPenalty += problemPenalty;
                    if (lastAcceptedAt == null || accepted.submittedAt().isAfter(lastAcceptedAt)) {
                        lastAcceptedAt = accepted.submittedAt();
                    }
                }
                problemScores.add(new ProblemScore(
                        problem.problemId(),
                        problem.label(),
                        accepted != null,
                        wrongAttempts,
                        problemPenalty,
                        accepted == null ? null : accepted.submittedAt(),
                        accepted != null
                                && accepted.submissionId()
                                        == firstAcceptedByProblem.getOrDefault(problem.problemId(), -1L)));
            }
            unordered.add(new Row(0, userId, solved, totalPenalty, lastAcceptedAt, List.copyOf(problemScores)));
        }

        Comparator<Instant> acceptedTime = Comparator.nullsLast(Comparator.naturalOrder());
        unordered.sort(Comparator.comparingInt(Row::solved)
                .reversed()
                .thenComparingInt(Row::penaltyMinutes)
                .thenComparing(Row::lastAcceptedAt, acceptedTime)
                .thenComparingLong(Row::userId));
        List<Row> ranked = new ArrayList<>();
        for (int index = 0; index < unordered.size(); index++) {
            Row row = unordered.get(index);
            ranked.add(new Row(
                    index + 1,
                    row.userId(),
                    row.solved(),
                    row.penaltyMinutes(),
                    row.lastAcceptedAt(),
                    row.problems()));
        }
        return new Scoreboard(List.copyOf(ranked));
    }

    private record UserProblem(long userId, long problemId) {}
}
