package com.zephyr.croj.contest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class OiScoreboardCalculator {
    private OiScoreboardCalculator() {}

    public record Problem(long problemId, String label, int maxScore) {}

    public record SubmissionFact(
            long submissionId,
            long userId,
            long problemId,
            Integer score,
            Instant submittedAt) {}

    public record ProblemScore(
            long problemId,
            String label,
            int maxScore,
            int score,
            Long submissionId,
            Instant achievedAt) {}

    public record Row(
            int rank,
            long userId,
            int totalScore,
            int scoredProblems,
            Instant lastImprovedAt,
            List<ProblemScore> problems) {}

    public record Scoreboard(int maximumScore, List<Row> rows) {}

    public static Scoreboard calculate(
            List<Long> participantIds,
            List<Problem> problems,
            List<SubmissionFact> submissions,
            Instant startsAt,
            Instant cutoffAt) {
        requireInputs(participantIds, problems, submissions, startsAt, cutoffAt);
        var participantSet = new HashSet<>(participantIds);
        var problemsById = new HashMap<Long, Problem>();
        var labels = new HashSet<String>();
        int maximumScore = 0;
        for (Problem problem : problems) {
            if (problem.maxScore() <= 0
                    || problemsById.put(problem.problemId(), problem) != null
                    || !labels.add(problem.label())) {
                throw new IllegalArgumentException(
                        "problem IDs and labels must be unique and maximum scores must be positive");
            }
            maximumScore = Math.addExact(maximumScore, problem.maxScore());
        }
        if (participantSet.size() != participantIds.size()) {
            throw new IllegalArgumentException("participant IDs must be unique");
        }

        Map<UserProblem, SubmissionFact> best = new HashMap<>();
        submissions.stream()
                .filter(fact -> !fact.submittedAt().isBefore(startsAt))
                .filter(fact -> fact.submittedAt().isBefore(cutoffAt))
                .filter(fact -> participantSet.contains(fact.userId()))
                .filter(fact -> problemsById.containsKey(fact.problemId()))
                .filter(fact -> fact.score() != null)
                .sorted(Comparator.comparing(SubmissionFact::submittedAt)
                        .thenComparingLong(SubmissionFact::submissionId))
                .forEach(fact -> {
                    Problem problem = problemsById.get(fact.problemId());
                    if (fact.score() < 0 || fact.score() > problem.maxScore()) {
                        throw new IllegalArgumentException(
                                "submission score is outside the pinned contest problem maximum");
                    }
                    best.merge(
                            new UserProblem(fact.userId(), fact.problemId()),
                            fact,
                            (current, candidate) -> candidate.score() > current.score()
                                    ? candidate
                                    : current);
                });

        List<Row> rows = new ArrayList<>();
        for (Long userId : participantIds) {
            int totalScore = 0;
            int scoredProblems = 0;
            Instant lastImprovedAt = null;
            List<ProblemScore> problemScores = new ArrayList<>();
            for (Problem problem : problems) {
                SubmissionFact fact = best.get(new UserProblem(userId, problem.problemId()));
                int score = fact == null ? 0 : fact.score();
                totalScore = Math.addExact(totalScore, score);
                if (score > 0) {
                    scoredProblems++;
                }
                if (score > 0
                        && (lastImprovedAt == null || fact.submittedAt().isAfter(lastImprovedAt))) {
                    lastImprovedAt = fact.submittedAt();
                }
                problemScores.add(new ProblemScore(
                        problem.problemId(),
                        problem.label(),
                        problem.maxScore(),
                        score,
                        fact == null ? null : fact.submissionId(),
                        fact == null ? null : fact.submittedAt()));
            }
            rows.add(new Row(
                    0,
                    userId,
                    totalScore,
                    scoredProblems,
                    lastImprovedAt,
                    List.copyOf(problemScores)));
        }

        Comparator<Instant> improvementTime = Comparator.nullsLast(Comparator.naturalOrder());
        rows.sort(Comparator.comparingInt(Row::totalScore)
                .reversed()
                .thenComparing(Comparator.comparingInt(Row::scoredProblems).reversed())
                .thenComparing(Row::lastImprovedAt, improvementTime)
                .thenComparingLong(Row::userId));
        List<Row> ranked = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            ranked.add(new Row(
                    index + 1,
                    row.userId(),
                    row.totalScore(),
                    row.scoredProblems(),
                    row.lastImprovedAt(),
                    row.problems()));
        }
        return new Scoreboard(maximumScore, List.copyOf(ranked));
    }

    private static void requireInputs(
            List<Long> participantIds,
            List<Problem> problems,
            List<SubmissionFact> submissions,
            Instant startsAt,
            Instant cutoffAt) {
        if (participantIds == null
                || problems == null
                || submissions == null
                || startsAt == null
                || cutoffAt == null) {
            throw new IllegalArgumentException("scoreboard inputs are required");
        }
        if (cutoffAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("scoreboard cutoff must not precede contest start");
        }
    }

    private record UserProblem(long userId, long problemId) {}
}
