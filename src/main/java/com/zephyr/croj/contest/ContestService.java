package com.zephyr.croj.contest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import com.zephyr.croj.model.dto.contest.ContestRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ContestService {
    private final ContestRepository contests;
    private final Clock clock;

    @Autowired
    public ContestService(ContestRepository contests) {
        this(contests, Clock.systemUTC());
    }

    public ContestService(ContestRepository contests, Clock clock) {
        this.contests = contests;
        this.clock = clock;
    }

    public String register(long contestId, long userId) {
        ContestRepository.ContestRecord contest = requireContest(contestId);
        ContestPhase phase = contest.phase(clock.instant());
        if (!ContestPolicy.canSelfRegister(contest.visibility(), phase)) {
            throw ContestApiException.conflict("contest does not allow self-registration now");
        }
        if (!contests.activeUserExists(userId)) {
            throw ContestApiException.unprocessable("participant must be an active user");
        }
        contests.register(contestId, userId, null);
        return "REGISTERED";
    }

    public String cancelRegistration(long contestId, long userId) {
        ContestRepository.ContestRecord contest = requireContest(contestId);
        Instant now = clock.instant();
        if (!"PUBLIC".equalsIgnoreCase(contest.visibility()) || !now.isBefore(contest.startsAt())) {
            throw ContestApiException.conflict("registration cannot be cancelled now");
        }
        contests.cancelRegistration(contestId, userId, null);
        return "CANCELLED";
    }

    public RegistrationStatus registrationStatus(long contestId, long userId) {
        requireContest(contestId);
        return new RegistrationStatus(contests.isRegistered(contestId, userId));
    }

    public ContestPage listPublic(int page, int requestedSize) {
        int safePage = Math.max(page, 1);
        int size = Math.min(Math.max(requestedSize, 1), 100);
        long offset = ((long) safePage - 1L) * size;
        List<ContestSummary> items = contests.listPublishedPublic(offset, size).stream()
                .map(contest -> new ContestSummary(
                        contest.id(),
                        contest.title(),
                        contest.ruleType(),
                        contest.phase(clock.instant()),
                        contest.startsAt(),
                        contest.endsAt()))
                .toList();
        return new ContestPage(safePage, size, contests.countPublishedPublic(), items);
    }

    public ContestDetail detail(long contestId, Long userId, boolean administrator) {
        ContestRepository.ContestRecord contest = requireReadable(contestId, userId, administrator);
        return new ContestDetail(contest, contest.phase(clock.instant()));
    }

    public List<ContestRepository.ContestProblem> problems(
            long contestId, Long userId, boolean administrator) {
        ContestRepository.ContestRecord contest = requireReadable(contestId, userId, administrator);
        boolean registered = userId != null && contests.isRegistered(contestId, userId);
        if (!ContestPolicy.canReadProblems(
                contest.visibility(), contest.phase(clock.instant()), registered, administrator)) {
            throw ContestApiException.forbidden("contest problems are not visible now");
        }
        return contests.listProblems(contestId);
    }

    public List<ContestRepository.AnnouncementView> announcements(
            long contestId, Long userId, boolean administrator) {
        requireReadable(contestId, userId, administrator);
        return contests.listAnnouncements(contestId);
    }

    public List<ContestRepository.ClarificationView> clarifications(
            long contestId, Long userId, boolean administrator) {
        ContestRepository.ContestRecord contest = requireReadable(contestId, userId, administrator);
        ContestPhase phase = contest.phase(clock.instant());
        boolean registered = userId != null && contests.isRegistered(contestId, userId);
        boolean publicReplies = registered
                || (phase == ContestPhase.ENDED && "PUBLIC".equals(contest.visibility()));
        return contests.listClarifications(contestId, userId, administrator, publicReplies);
    }

    public long ask(long contestId, long userId, ContestRequests.Clarification request) {
        ContestRepository.ContestRecord contest = requireContest(contestId);
        ContestPhase phase = contest.phase(clock.instant());
        if ((phase != ContestPhase.RUNNING && phase != ContestPhase.FROZEN)
                || !contests.isRegistered(contestId, userId)) {
            throw ContestApiException.forbidden("only active participants may ask during the contest");
        }
        if (request.problemId() != null && contests.problemVersion(contestId, request.problemId()).isEmpty()) {
            throw ContestApiException.unprocessable("clarification problem is not in this contest");
        }
        return contests.addClarification(contestId, userId, request);
    }

    public long validateSubmission(long contestId, long userId, long problemId) {
        ContestRepository.ContestRecord contest = requireContest(contestId);
        ContestPhase phase = contest.phase(clock.instant());
        if ((phase != ContestPhase.RUNNING && phase != ContestPhase.FROZEN)
                || !contests.isRegistered(contestId, userId)) {
            throw ContestApiException.forbidden("contest submission requires an active registration and running contest");
        }
        long versionId = contests.problemVersion(contestId, problemId)
                .orElseThrow(() -> ContestApiException.unprocessable("problem is not arranged in this contest"));
        if (!contests.isAvailableProblemVersion(problemId, versionId)) {
            throw ContestApiException.unprocessable("contest problem version is not judge ready");
        }
        return versionId;
    }

    private ContestRepository.ContestRecord requireReadable(
            long contestId, Long userId, boolean administrator) {
        ContestRepository.ContestRecord contest = requireContest(contestId);
        if (administrator) {
            return contest;
        }
        if (!"PUBLISHED".equals(contest.lifecycle())) {
            throw ContestApiException.notFound();
        }
        if ("PRIVATE".equals(contest.visibility())
                && (userId == null || !contests.isRegistered(contestId, userId))) {
            throw ContestApiException.forbidden("private contest requires managed registration");
        }
        return contest;
    }

    public ContestRepository.ContestRecord requireContest(long contestId) {
        return contests.findById(contestId).orElseThrow(ContestApiException::notFound);
    }

    public record RegistrationStatus(boolean registered) {}
    public record ContestSummary(
            long id, String title, String ruleType, ContestPhase phase, Instant startsAt, Instant endsAt) {}
    public record ContestPage(int page, int size, long total, List<ContestSummary> items) {}
    public record ContestDetail(ContestRepository.ContestRecord contest, ContestPhase phase) {}
}
