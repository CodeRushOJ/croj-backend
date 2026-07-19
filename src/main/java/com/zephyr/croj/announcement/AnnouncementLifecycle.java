package com.zephyr.croj.announcement;

import java.time.Instant;
import java.util.Locale;

public enum AnnouncementLifecycle {
    DRAFT,
    SCHEDULED,
    PUBLISHED,
    EXPIRED,
    ARCHIVED;

    public static AnnouncementLifecycle parseFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw AnnouncementApiException.badRequest("unknown announcement status");
        }
    }

    public static AnnouncementLifecycle effective(
            AnnouncementLifecycle stored, Instant publishAt, Instant expiresAt, Instant now) {
        if (stored == ARCHIVED || stored == DRAFT) {
            return stored;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return EXPIRED;
        }
        if (stored == SCHEDULED && publishAt != null && !publishAt.isAfter(now)) {
            return PUBLISHED;
        }
        return stored;
    }
}
