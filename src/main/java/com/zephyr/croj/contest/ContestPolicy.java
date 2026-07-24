package com.zephyr.croj.contest;

import java.time.Instant;
import java.util.Locale;

public final class ContestPolicy {
    private ContestPolicy() {}

    public static ContestPhase phase(
            String lifecycle,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant freezeAt,
            Instant endsAt,
            Instant now) {
        String state = lifecycle.toUpperCase(Locale.ROOT);
        if ("DRAFT".equals(state)) {
            return ContestPhase.DRAFT;
        }
        if ("CANCELLED".equals(state)) {
            return ContestPhase.CANCELLED;
        }
        if (!now.isBefore(endsAt)) {
            return ContestPhase.ENDED;
        }
        if (freezeAt != null && !now.isBefore(freezeAt)) {
            return ContestPhase.FROZEN;
        }
        if (!now.isBefore(startsAt)) {
            return ContestPhase.RUNNING;
        }
        if (!now.isBefore(registrationOpensAt) && now.isBefore(registrationClosesAt)) {
            return ContestPhase.REGISTRATION;
        }
        return ContestPhase.SCHEDULED;
    }

    public static void validateSchedule(
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            Instant startsAt,
            Instant freezeAt,
            Instant endsAt) {
        if (registrationOpensAt == null
                || registrationClosesAt == null
                || startsAt == null
                || endsAt == null
                || registrationOpensAt.isAfter(registrationClosesAt)
                || registrationClosesAt.isAfter(startsAt)
                || !startsAt.isBefore(endsAt)
                || (freezeAt != null && (!startsAt.isBefore(freezeAt) || !freezeAt.isBefore(endsAt)))) {
            throw new IllegalArgumentException("invalid contest registration, start, freeze, or end time ordering");
        }
    }

    public static boolean canReadScoreboard(ContestPhase phase) {
        return phase == ContestPhase.RUNNING
                || phase == ContestPhase.FROZEN
                || phase == ContestPhase.ENDED;
    }

    public static boolean canReadProblems(
            String visibility, ContestPhase phase, boolean registered, boolean administrator) {
        if (administrator) {
            return true;
        }
        if (phase == ContestPhase.RUNNING || phase == ContestPhase.FROZEN) {
            return registered;
        }
        if (phase == ContestPhase.ENDED) {
            return "PUBLIC".equalsIgnoreCase(visibility) || registered;
        }
        return false;
    }

    public static boolean canSelfRegister(String visibility, ContestPhase phase) {
        return "PUBLIC".equalsIgnoreCase(visibility) && phase == ContestPhase.REGISTRATION;
    }
}
