package com.zephyr.croj.contest;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ContestApiException extends RuntimeException {
    private final HttpStatus status;

    public ContestApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ContestApiException notFound() {
        return new ContestApiException(HttpStatus.NOT_FOUND, "contest not found");
    }

    public static ContestApiException forbidden(String message) {
        return new ContestApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ContestApiException conflict(String message) {
        return new ContestApiException(HttpStatus.CONFLICT, message);
    }

    public static ContestApiException unprocessable(String message) {
        return new ContestApiException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
