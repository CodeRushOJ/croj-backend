package com.zephyr.croj.contest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
        String sourceVersion = contests.scoreboardSourceVersion(contest.id(), cutoffExclusive);
        if (cacheable) {
            var cached = readSnapshot(contest.id(), cutoffExclusive, sourceVersion);
            if (cached != null) {
                return new ScoreboardView(
                        contest.id(),
                        contest.ruleType(),
                        cutoffExclusive,
                        frozen,
                        sourceVersion,
                        cached.maximumScore(),
                        cached.rows());
            }
        }
        var participants = contests.registeredParticipants(contest.id());
        SnapshotPayload snapshot;
        if ("ACM".equalsIgnoreCase(contest.ruleType())) {
            snapshot = acmBoard(contest, participants, cutoffExclusive);
        } else if ("OI".equalsIgnoreCase(contest.ruleType())) {
            snapshot = oiBoard(contest, participants, cutoffExclusive);
        } else {
            throw new IllegalStateException("persisted contest rule type is unsupported");
        }
        if (cacheable) {
            writeSnapshot(contest.id(), cutoffExclusive, sourceVersion, snapshot);
        }
        return new ScoreboardView(
                contest.id(),
                contest.ruleType(),
                cutoffExclusive,
                frozen,
                sourceVersion,
                snapshot.maximumScore(),
                snapshot.rows());
    }

    private SnapshotPayload acmBoard(
            ContestRepository.ContestRecord contest,
            List<ContestRepository.Participant> participants,
            Instant cutoffExclusive) {
        var problems = contests.listProblems(contest.id()).stream()
                .map(problem -> new AcmScoreboardCalculator.Problem(problem.problemId(), problem.label()))
                .toList();
        var board = AcmScoreboardCalculator.calculate(
                participantIds(participants),
                problems,
                contests.submissionFacts(contest.id(), cutoffExclusive),
                contest.startsAt(),
                cutoffExclusive);
        Map<Long, String> participantNames = participantNames(participants);
        return new SnapshotPayload(
                null,
                board.rows().stream()
                        .map(row -> ScoreboardRow.fromAcm(row, participantNames))
                        .toList());
    }

    private SnapshotPayload oiBoard(
            ContestRepository.ContestRecord contest,
            List<ContestRepository.Participant> participants,
            Instant cutoffExclusive) {
        var problems = contests.listProblems(contest.id()).stream()
                .map(problem -> new OiScoreboardCalculator.Problem(
                        problem.problemId(), problem.label(), problem.score()))
                .toList();
        var board = OiScoreboardCalculator.calculate(
                participantIds(participants),
                problems,
                contests.oiSubmissionFacts(contest.id(), cutoffExclusive),
                contest.startsAt(),
                cutoffExclusive);
        Map<Long, String> participantNames = participantNames(participants);
        return new SnapshotPayload(
                board.maximumScore(),
                board.rows().stream()
                        .map(row -> ScoreboardRow.fromOi(row, participantNames))
                        .toList());
    }

    private List<Long> participantIds(List<ContestRepository.Participant> participants) {
        return participants.stream().map(ContestRepository.Participant::userId).toList();
    }

    private Map<Long, String> participantNames(List<ContestRepository.Participant> participants) {
        return participants.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ContestRepository.Participant::userId,
                        ContestRepository.Participant::username));
    }

    private SnapshotPayload readSnapshot(long contestId, Instant cutoff, String sourceVersion) {
        return contests.findScoreboardSnapshot(contestId, "PUBLIC", cutoff, sourceVersion)
                .flatMap(payload -> {
                    try {
                        JsonNode root = objectMapper.readTree(payload);
                        if (root.isTextual()) {
                            root = objectMapper.readTree(root.textValue());
                        }
                        if (!root.isObject()) {
                            throw new JsonProcessingException("scoreboard snapshot root must be an object") {};
                        }
                        return java.util.Optional.of(objectMapper.treeToValue(root, SnapshotPayload.class));
                    } catch (JsonProcessingException exception) {
                        log.warn("discarding unreadable contest scoreboard snapshot: contestId={}", contestId);
                        log.debug("unreadable contest scoreboard snapshot details", exception);
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

    record SnapshotPayload(Integer maximumScore, List<ScoreboardRow> rows) {}

    public record ScoreboardView(
            long contestId,
            String ruleType,
            Instant cutoffExclusive,
            boolean frozen,
            String sourceVersion,
            Integer maximumScore,
            List<ScoreboardRow> rows) {}

    public record ScoreboardRow(
            int rank,
            long userId,
            String username,
            Integer solved,
            Integer penaltyMinutes,
            Instant lastAcceptedAt,
            Integer totalScore,
            Integer scoredProblems,
            Instant lastImprovedAt,
            List<ProblemScore> problems) {
        static ScoreboardRow fromAcm(
                AcmScoreboardCalculator.Row row, Map<Long, String> participantNames) {
            return new ScoreboardRow(
                    row.rank(),
                    row.userId(),
                    participantNames.get(row.userId()),
                    row.solved(),
                    row.penaltyMinutes(),
                    row.lastAcceptedAt(),
                    null,
                    null,
                    null,
                    row.problems().stream().map(ProblemScore::fromAcm).toList());
        }

        static ScoreboardRow fromOi(
                OiScoreboardCalculator.Row row, Map<Long, String> participantNames) {
            return new ScoreboardRow(
                    row.rank(),
                    row.userId(),
                    participantNames.get(row.userId()),
                    null,
                    null,
                    null,
                    row.totalScore(),
                    row.scoredProblems(),
                    row.lastImprovedAt(),
                    row.problems().stream().map(ProblemScore::fromOi).toList());
        }
    }

    public record ProblemScore(
            long problemId,
            String label,
            Boolean accepted,
            Integer wrongAttempts,
            Integer penaltyMinutes,
            Instant acceptedAt,
            Boolean firstAccepted,
            Integer maximumScore,
            Integer score,
            Long submissionId,
            Instant achievedAt) {
        static ProblemScore fromAcm(AcmScoreboardCalculator.ProblemScore score) {
            return new ProblemScore(
                    score.problemId(),
                    score.label(),
                    score.accepted(),
                    score.wrongAttempts(),
                    score.penaltyMinutes(),
                    score.acceptedAt(),
                    score.firstAccepted(),
                    null,
                    null,
                    null,
                    null);
        }

        static ProblemScore fromOi(OiScoreboardCalculator.ProblemScore score) {
            return new ProblemScore(
                    score.problemId(),
                    score.label(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    score.maxScore(),
                    score.score(),
                    score.submissionId(),
                    score.achievedAt());
        }
    }
}
