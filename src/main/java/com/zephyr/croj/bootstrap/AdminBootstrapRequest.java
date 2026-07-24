package com.zephyr.croj.bootstrap;

public record AdminBootstrapRequest(String username, String email, String password) {

    @Override
    public String toString() {
        return "AdminBootstrapRequest[username=<redacted>, email=<redacted>, password=<redacted>]";
    }
}
