package com.zephyr.croj.announcement;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AnnouncementApiException extends RuntimeException {
    private final HttpStatus status;

    public AnnouncementApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static AnnouncementApiException notFound() {
        return new AnnouncementApiException(HttpStatus.NOT_FOUND, "announcement not found");
    }

    public static AnnouncementApiException badRequest(String message) {
        return new AnnouncementApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static AnnouncementApiException conflict() {
        return new AnnouncementApiException(HttpStatus.CONFLICT, "announcement changed concurrently");
    }

    public static AnnouncementApiException unprocessable(String message) {
        return new AnnouncementApiException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
