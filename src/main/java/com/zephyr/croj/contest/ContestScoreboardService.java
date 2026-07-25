package com.zephyr.croj.contest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
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
        var participants = contests.registeredParticipants(contest.id());
        var problems = contests.listProblems(contest.id());
        SnapshotPayload snapshot;
        String sourceVersion;
        boolean snapshotComputed = false;
        if ("ACM".equalsIgnoreCase(contest.ruleType())) {
            var facts = contests.submissionFacts(contest.id(), cutoffExclusive);
            sourceVersion = sourceVersion(participants, problems, facts, List.of());
            snapshot = cacheable
                    ? readSnapshot(contest, cutoffExclusive, sourceVersion)
                    : null;
            if (snapshot == null) {
                snapshot = acmBoard(contest, participants, problems, facts, cutoffExclusive);
                snapshotComputed = true;
            }
        } else if ("OI".equalsIgnoreCase(contest.ruleType())) {
            var facts = contests.oiSubmissionFacts(contest.id(), cutoffExclusive);
            sourceVersion = sourceVersion(participants, problems, List.of(), facts);
            snapshot = cacheable
                    ? readSnapshot(contest, cutoffExclusive, sourceVersion)
                    : null;
            if (snapshot == null) {
                snapshot = oiBoard(contest, participants, problems, facts, cutoffExclusive);
                snapshotComputed = true;
            }
        } else {
            throw new IllegalStateException("persisted contest rule type is unsupported");
        }
        if (cacheable && snapshotComputed) {
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
            List<ContestRepository.ContestProblem> pinnedProblems,
            List<AcmScoreboardCalculator.SubmissionFact> facts,
            Instant cutoffExclusive) {
        var problems = pinnedProblems.stream()
                .map(problem -> new AcmScoreboardCalculator.Problem(problem.problemId(), problem.label()))
                .toList();
        var board = AcmScoreboardCalculator.calculate(
                participantIds(participants),
                problems,
                facts,
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
            List<ContestRepository.ContestProblem> pinnedProblems,
            List<OiScoreboardCalculator.SubmissionFact> facts,
            Instant cutoffExclusive) {
        var problems = pinnedProblems.stream()
                .map(problem -> new OiScoreboardCalculator.Problem(
                        problem.problemId(), problem.label(), problem.score()))
                .toList();
        var board = OiScoreboardCalculator.calculate(
                participantIds(participants),
                problems,
                facts,
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

    private SnapshotPayload readSnapshot(
            ContestRepository.ContestRecord contest, Instant cutoff, String sourceVersion) {
        return contests.findScoreboardSnapshot(contest.id(), "PUBLIC", cutoff, sourceVersion)
                .flatMap(payload -> {
                    try {
                        JsonNode root = objectMapper.readTree(payload);
                        if (root.isTextual()) {
                            root = objectMapper.readTree(root.textValue());
                        }
                        if (!root.isObject()) {
                            throw new JsonProcessingException("scoreboard snapshot root must be an object") {};
                        }
                        SnapshotPayload snapshot = objectMapper.treeToValue(root, SnapshotPayload.class);
                        if (!validSnapshot(contest.ruleType(), snapshot)) {
                            throw new JsonProcessingException("scoreboard snapshot is semantically invalid") {};
                        }
                        return java.util.Optional.of(snapshot);
                    } catch (JsonProcessingException exception) {
                        log.warn("discarding unreadable contest scoreboard snapshot: contestId={}", contest.id());
                        log.debug("unreadable contest scoreboard snapshot details", exception);
                        return java.util.Optional.empty();
                    }
                })
                .orElse(null);
    }

    private boolean validSnapshot(String ruleType, SnapshotPayload snapshot) {
        if (snapshot == null || snapshot.rows() == null) {
            return false;
        }
        boolean oi = "OI".equalsIgnoreCase(ruleType);
        if ((oi && (snapshot.maximumScore() == null || snapshot.maximumScore() < 0))
                || (!oi && snapshot.maximumScore() != null)) {
            return false;
        }
        for (int index = 0; index < snapshot.rows().size(); index++) {
            ScoreboardRow row = snapshot.rows().get(index);
            if (row == null
                    || row.rank() != index + 1
                    || row.username() == null
                    || row.username().isBlank()
                    || row.problems() == null) {
                return false;
            }
            if (oi) {
                if (row.totalScore() == null
                        || row.solved() != null) {
                    return false;
                }
            } else if (row.solved() == null
                    || row.totalScore() != null) {
                return false;
            }
        }
        return true;
    }

    private String sourceVersion(
            List<ContestRepository.Participant> participants,
            List<ContestRepository.ContestProblem> problems,
            List<AcmScoreboardCalculator.SubmissionFact> acmFacts,
            List<OiScoreboardCalculator.SubmissionFact> oiFacts) {
        MessageDigest digest = sha256();
        digestField(digest, "contest-scoreboard-v4");
        participants.forEach(participant -> {
            digestField(digest, "participant");
            digestField(digest, participant.userId());
            digestField(digest, participant.username());
        });
        problems.forEach(problem -> {
            digestField(digest, "problem");
            digestField(digest, problem.problemId());
            digestField(digest, problem.problemVersionId());
            digestField(digest, problem.label());
            digestField(digest, problem.score());
        });
        acmFacts.forEach(fact -> {
            digestField(digest, "acm-submission");
            digestField(digest, fact.submissionId());
            digestField(digest, fact.userId());
            digestField(digest, fact.problemId());
            digestField(digest, fact.status());
            digestField(digest, fact.submittedAt().toEpochMilli());
        });
        oiFacts.forEach(fact -> {
            digestField(digest, "oi-submission");
            digestField(digest, fact.submissionId());
            digestField(digest, fact.userId());
            digestField(digest, fact.problemId());
            digestField(digest, fact.score());
            digestField(digest, fact.submittedAt().toEpochMilli());
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void digestField(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
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

    @Schema(
            description =
                    "Contest scoreboard. ruleType selects the ACM or OI field family; fields from the other family are null.")
    public record ScoreboardView(
            long contestId,
            @Schema(description = "Scoring contract discriminator", allowableValues = {"ACM", "OI"})
            String ruleType,
            @Schema(description = "Exclusive submission cutoff; submissions exactly at this instant are excluded")
            Instant cutoffExclusive,
            @Schema(description = "Whether this is the frozen public view")
            boolean frozen,
            String sourceVersion,
            @Schema(description = "OI total available score; null for ACM")
            Integer maximumScore,
            List<ScoreboardRow> rows) {}

    @Schema(description = "One ranked participant row with mutually exclusive ACM and OI metrics")
    public record ScoreboardRow(
            int rank,
            long userId,
            String username,
            @Schema(description = "ACM solved count; null for OI")
            Integer solved,
            @Schema(description = "ACM penalty in minutes; null for OI")
            Integer penaltyMinutes,
            @Schema(description = "ACM last accepted time; null for OI")
            Instant lastAcceptedAt,
            @Schema(description = "OI best-score sum; null for ACM")
            Integer totalScore,
            @Schema(description = "OI problems with a positive best score; null for ACM")
            Integer scoredProblems,
            @Schema(description = "OI last score-improvement time; null for ACM")
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

    @Schema(description = "Per-problem ACM acceptance metrics or OI best-score metrics")
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
