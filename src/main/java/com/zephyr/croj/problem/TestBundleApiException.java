package com.zephyr.croj.problem;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TestBundleApiException extends RuntimeException {
    private final HttpStatus status;

    private TestBundleApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static TestBundleApiException badRequest(String message) {
        return new TestBundleApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static TestBundleApiException notFound() {
        return new TestBundleApiException(HttpStatus.NOT_FOUND, "problem version not found");
    }

    public static TestBundleApiException payloadTooLarge() {
        return new TestBundleApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "test bundle archive exceeds the configured upload limit");
    }

    public static TestBundleApiException unprocessable() {
        return new TestBundleApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "test bundle is invalid or exceeds contract limits");
    }

    public static TestBundleApiException conflict(String message) {
        return new TestBundleApiException(HttpStatus.CONFLICT, message);
    }

    public static TestBundleApiException preconditionRequired() {
        return new TestBundleApiException(
                HttpStatus.PRECONDITION_REQUIRED,
                "If-Match is required; read the current test bundle metadata first");
    }

    public static TestBundleApiException preconditionFailed() {
        return new TestBundleApiException(
                HttpStatus.PRECONDITION_FAILED,
                "problem version or test bundle changed concurrently");
    }
}
