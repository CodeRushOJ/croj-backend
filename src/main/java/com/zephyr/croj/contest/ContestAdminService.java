package com.zephyr.croj.contest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.model.dto.contest.ContestRequests;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContestAdminService {
    private final ContestRepository contests;
    private final ObjectMapper objectMapper;

    @Autowired
    public ContestAdminService(ContestRepository contests, ObjectMapper objectMapper) {
        this.contests = contests;
        this.objectMapper = objectMapper;
    }

    ContestAdminService(ContestRepository contests) {
        this(contests, new ObjectMapper());
    }

    @Transactional
    public long create(ContestRequests.Upsert request, long administratorId) {
        validate(request);
        return contests.create(request, administratorId);
    }

    @Transactional
    public void update(long contestId, ContestRequests.Upsert request) {
        validate(request);
        if (contests.updateDraft(contestId, request) != 1) {
            throw ContestApiException.conflict("only a draft contest can be updated");
        }
    }

    @Transactional
    public void arrangeProblems(long contestId, ContestRequests.ProblemArrangement request) {
        ContestRepository.ContestRecord contest = contests.findByIdForUpdate(contestId)
                .orElseThrow(ContestApiException::notFound);
        if (!"DRAFT".equals(contest.lifecycle())) {
            throw ContestApiException.conflict("problem arrangement is immutable after publication");
        }
        var problemIds = new HashSet<Long>();
        var labels = new HashSet<String>();
        request.problems().forEach(problem -> {
            problemIds.add(problem.problemId());
            labels.add(problem.label().trim().toUpperCase(Locale.ROOT));
        });
        if (problemIds.size() != request.problems().size() || labels.size() != request.problems().size()) {
            throw ContestApiException.unprocessable("contest problem IDs and labels must be unique");
        }
        request.problems().forEach(problem -> {
            ContestRepository.ContestProblemRule pinned = contests
                    .findAvailableProblemVersionRule(problem.problemId(), problem.problemVersionId())
                    .orElseThrow(() -> ContestApiException.unprocessable(
                            "problemVersionId must be a published immutable version of problemId"));
            validateProblemRule(
                    contest.ruleType(),
                    new ContestRepository.ContestProblemRule(
                            pinned.problemId(),
                            pinned.problemVersionId(),
                            problem.score(),
                            pinned.limitsJson(),
                            pinned.judgeConfigJson()),
                    false);
        });
        contests.replaceProblems(contestId, request.problems());
    }

    @Transactional
    public void publish(long contestId) {
        ContestRepository.ContestRecord contest = contests.findByIdForUpdate(contestId)
                .orElseThrow(ContestApiException::notFound);
        validateSchedule(
                contest.registrationOpensAt(),
                contest.registrationClosesAt(),
                contest.startsAt(),
                contest.freezeAt(),
                contest.endsAt());
        int problemCount = contests.problemCount(contestId);
        if (problemCount == 0) {
            throw ContestApiException.conflict("a contest requires at least one problem before publication");
        }
        List<ContestRepository.ContestProblemRule> problemRules =
                contests.listContestProblemRules(contestId);
        if (problemRules.size() != problemCount) {
            throw ContestApiException.conflict(
                    "all contest problems must remain published with an attached test bundle");
        }
        problemRules.forEach(problem -> validateProblemRule(contest.ruleType(), problem, true));
        if (contests.transitionLifecycle(contestId, "DRAFT", "PUBLISHED") != 1) {
            throw ContestApiException.conflict("contest publication state changed concurrently");
        }
    }

    public void cancel(long contestId) {
        if (contests.cancel(contestId) != 1) {
            throw ContestApiException.conflict("contest is missing or already cancelled");
        }
    }

    public String manageRegistration(long contestId, long userId, long administratorId, boolean registered) {
        contests.findById(contestId).orElseThrow(ContestApiException::notFound);
        if (registered) {
            if (!contests.activeUserExists(userId)) {
                throw ContestApiException.unprocessable("managed participant must be an active user");
            }
            contests.register(contestId, userId, administratorId);
            return "REGISTERED";
        }
        contests.cancelRegistration(contestId, userId, administratorId);
        return "CANCELLED";
    }

    public long announce(long contestId, ContestRequests.Announcement request, long administratorId) {
        contests.findById(contestId).orElseThrow(ContestApiException::notFound);
        return contests.addAnnouncement(contestId, request, administratorId);
    }

    public long reply(
            long contestId,
            long clarificationId,
            ContestRequests.ClarificationReply request,
            long administratorId) {
        contests.findById(contestId).orElseThrow(ContestApiException::notFound);
        if (!contests.clarificationBelongsToContest(clarificationId, contestId)) {
            throw ContestApiException.notFound();
        }
        return contests.addClarificationReply(clarificationId, request, administratorId);
    }

    private void validate(ContestRequests.Upsert request) {
        validateSchedule(
                request.registrationOpensAt(),
                request.registrationClosesAt(),
                request.startsAt(),
                request.freezeAt(),
                request.endsAt());
        String rule = request.ruleType().trim().toUpperCase(Locale.ROOT);
        String visibility = request.visibility().trim().toUpperCase(Locale.ROOT);
        if (!rule.equals("ACM") && !rule.equals("OI")) {
            throw ContestApiException.unprocessable("ruleType must be ACM or OI");
        }
        if (!visibility.equals("PUBLIC") && !visibility.equals("PRIVATE")) {
            throw ContestApiException.unprocessable("visibility must be PUBLIC or PRIVATE");
        }
    }

    private void validateProblemRule(
            String contestRuleType,
            ContestRepository.ContestProblemRule problem,
            boolean publication) {
        try {
            JsonNode limits = objectMapper.readTree(problem.limitsJson());
            JsonNode judgeConfig = objectMapper.readTree(problem.judgeConfigJson());
            JsonNode judgeModeNode = judgeConfig.path("judgeMode");
            JsonNode totalScoreNode = limits.path("totalScore");
            if (!judgeModeNode.isIntegralNumber()
                    || !totalScoreNode.isIntegralNumber()
                    || totalScoreNode.intValue() <= 0) {
                throw invalidProblemRule(publication);
            }
            int expectedMode = "OI".equals(contestRuleType) ? 1 : 0;
            if (judgeModeNode.intValue() != expectedMode) {
                throw invalidProblemRule(publication);
            }
            if (expectedMode == 1 && problem.score() != totalScoreNode.intValue()) {
                throw invalidProblemRule(publication);
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ContestApiException contestApiException) {
                throw contestApiException;
            }
            throw invalidProblemRule(publication);
        }
    }

    private ContestApiException invalidProblemRule(boolean publication) {
        String message =
                "contest rule, immutable judgeMode, test-bundle totalScore, and arranged score must agree";
        return publication
                ? ContestApiException.conflict(message)
                : ContestApiException.unprocessable(message);
    }

    private void validateSchedule(
            java.time.Instant registrationOpensAt,
            java.time.Instant registrationClosesAt,
            java.time.Instant startsAt,
            java.time.Instant freezeAt,
            java.time.Instant endsAt) {
        try {
            ContestPolicy.validateSchedule(
                    registrationOpensAt, registrationClosesAt, startsAt, freezeAt, endsAt);
        } catch (IllegalArgumentException exception) {
            throw ContestApiException.unprocessable(exception.getMessage());
        }
    }
}
