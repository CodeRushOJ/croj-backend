package com.zephyr.croj.contest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ContestScoreboardService {
    private final ContestRepository contests;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public ContestScoreboardService(ContestRepository contests, ObjectMapper objectMapper) {
        this(contests, Clock.systemUTC(), objectMapper);
    }

    public ContestScoreboardService(ContestRepository contests, Clock clock) {
        this(contests, clock, new ObjectMapper().findAndRegisterModules());
    }

    ContestScoreboardService(ContestRepository contests, Clock clock, ObjectMapper objectMapper) {
        this.contests = contests;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ScoreboardView publicScoreboard(long contestId, Long userId) {
        ContestRepository.ContestRecord contest = contests.findById(contestId)
                .orElseThrow(ContestApiException::notFound);
        ContestPhase phase = contest.phase(clock.instant());
        if (!ContestPolicy.canReadScoreboard(phase)) {
            throw ContestApiException.forbidden("scoreboard is hidden before the contest starts");
        }
        if ("PRIVATE".equals(contest.visibility())
                && (userId == null || !contests.isRegistered(contestId, userId))) {
            throw ContestApiException.forbidden("private contest scoreboard requires registration");
        }
        Instant cutoff = switch (phase) {
            case FROZEN -> contest.freezeAt();
            case ENDED -> contest.endsAt();
            default -> clock.instant();
        };
        boolean stablePublicCutoff = phase == ContestPhase.FROZEN || phase == ContestPhase.ENDED;
        return build(contest, cutoff, phase == ContestPhase.FROZEN, stablePublicCutoff);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ScoreboardView administratorScoreboard(long contestId) {
        ContestRepository.ContestRecord contest = contests.findById(contestId)
                .orElseThrow(ContestApiException::notFound);
        Instant now = clock.instant();
        Instant cutoff = now.isBefore(contest.endsAt()) ? now : contest.endsAt();
        return build(contest, cutoff, false, false);
    }

    private ScoreboardView build(
            ContestRepository.ContestRecord contest,
            Instant cutoffExclusive,
            boolean frozen,
            boolean cacheable) {
        if (!"ACM".equalsIgnoreCase(contest.ruleType())) {
            throw ContestApiException.unprocessable("OI scoreboard is not implemented in Contest Core v1");
        }
        String sourceVersion = contests.scoreboardSourceVersion(contest.id(), cutoffExclusive);
        if (cacheable) {
            var cached = readSnapshot(contest.id(), cutoffExclusive, sourceVersion);
            if (cached != null) {
                return new ScoreboardView(
                        contest.id(), cutoffExclusive, frozen, sourceVersion, cached.rows());
            }
        }
        var participants = contests.registeredUsers(contest.id());
        var problems = contests.listProblems(contest.id()).stream()
                .map(problem -> new AcmScoreboardCalculator.Problem(problem.problemId(), problem.label()))
                .toList();
        var board = AcmScoreboardCalculator.calculate(
                participants,
                problems,
                contests.submissionFacts(contest.id(), cutoffExclusive),
                contest.startsAt(),
                cutoffExclusive);
        if (cacheable) {
            writeSnapshot(contest.id(), cutoffExclusive, sourceVersion, new SnapshotPayload(board.rows()));
        }
        return new ScoreboardView(contest.id(), cutoffExclusive, frozen, sourceVersion, board.rows());
    }

    private SnapshotPayload readSnapshot(long contestId, Instant cutoff, String sourceVersion) {
        return contests.findScoreboardSnapshot(contestId, "PUBLIC", cutoff, sourceVersion)
                .flatMap(payload -> {
                    try {
                        return java.util.Optional.of(objectMapper.readValue(payload, SnapshotPayload.class));
                    } catch (JsonProcessingException exception) {
                        log.warn("discarding unreadable contest scoreboard snapshot: contestId={}", contestId);
                        return java.util.Optional.empty();
                    }
                })
                .orElse(null);
    }

    private void writeSnapshot(
            long contestId, Instant cutoff, String sourceVersion, SnapshotPayload snapshot) {
        try {
            contests.saveScoreboardSnapshot(
                    contestId, "PUBLIC", cutoff, sourceVersion, objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            log.warn("unable to serialize contest scoreboard snapshot: contestId={}", contestId);
        }
    }

    record SnapshotPayload(List<AcmScoreboardCalculator.Row> rows) {}

    public record ScoreboardView(
            long contestId,
            Instant cutoffExclusive,
            boolean frozen,
            String sourceVersion,
            java.util.List<AcmScoreboardCalculator.Row> rows) {}
}
