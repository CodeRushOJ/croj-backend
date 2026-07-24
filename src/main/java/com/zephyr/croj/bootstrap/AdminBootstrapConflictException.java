package com.zephyr.croj.bootstrap;

public final class AdminBootstrapConflictException extends IllegalStateException {

    public AdminBootstrapConflictException() {
        super("the bootstrap identity conflicts with an existing account");
    }
}
