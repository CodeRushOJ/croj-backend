package com.coderushoj.container;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ActuatorHealthCheckContractTest {

    private static final int STATUS_TEST_TIMEOUT_MILLIS = 1_000;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsSuccessfulNoContentResponses() throws Exception {
        URL endpoint = endpointReturning(204, new byte[0]);

        assertEquals(0, ActuatorHealthCheck.check(
                endpoint,
                STATUS_TEST_TIMEOUT_MILLIS,
                STATUS_TEST_TIMEOUT_MILLIS));
    }

    @Test
    void rejectsRedirectsInsteadOfFollowingThem() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://example.invalid/secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        assertEquals(1, ActuatorHealthCheck.check(
                localUrl(),
                STATUS_TEST_TIMEOUT_MILLIS,
                STATUS_TEST_TIMEOUT_MILLIS));
    }

    @Test
    void rejectsNonTwoHundredWithoutPrintingItsBody() throws Exception {
        byte[] secretBody = "do-not-leak-health-details".getBytes(StandardCharsets.UTF_8);
        URL endpoint = endpointReturning(503, secretBody);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        int result;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            result = ActuatorHealthCheck.check(
                    endpoint,
                    STATUS_TEST_TIMEOUT_MILLIS,
                    STATUS_TEST_TIMEOUT_MILLIS);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        assertEquals(1, result);
        assertFalse(stdout.toString(StandardCharsets.UTF_8).contains("do-not-leak"));
        assertFalse(stderr.toString(StandardCharsets.UTF_8).contains("do-not-leak"));
    }

    @Test
    void enforcesReadTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            try {
                Thread.sleep(500);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        assertTimeoutPreemptively(Duration.ofMillis(400),
                () -> assertEquals(1, ActuatorHealthCheck.check(localUrl(), 100, 50)));
    }

    private URL endpointReturning(int status, byte[] body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        return localUrl();
    }

    private URL localUrl() throws Exception {
        return new URL("http", "127.0.0.1", server.getAddress().getPort(), "/health");
    }
}
