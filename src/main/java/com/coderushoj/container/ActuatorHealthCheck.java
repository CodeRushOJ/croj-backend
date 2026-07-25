package com.coderushoj.container;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;

/**
 * Shell-free OCI healthcheck for the local Spring Boot Actuator liveness group.
 *
 * <p>The production entrypoint always uses the fixed loopback endpoint. The
 * package-private overload exists only so tests can exercise status and timeout
 * behavior with an ephemeral loopback server.</p>
 */
public final class ActuatorHealthCheck {

    private static final String LIVENESS_URL =
            "http://127.0.0.1:7999/api/actuator/health/liveness";
    private static final URL LIVENESS_ENDPOINT = livenessEndpoint();
    private static final int CONNECT_TIMEOUT_MILLIS = 1_000;
    private static final int READ_TIMEOUT_MILLIS = 1_000;

    private ActuatorHealthCheck() {
    }

    public static void main(String[] args) {
        int result = check(
                LIVENESS_ENDPOINT,
                CONNECT_TIMEOUT_MILLIS,
                READ_TIMEOUT_MILLIS);
        if (result != 0) {
            System.exit(result);
        }
    }

    private static URL livenessEndpoint() {
        try {
            return URI.create(LIVENESS_URL).toURL();
        } catch (MalformedURLException malformedUrl) {
            throw new ExceptionInInitializerError(malformedUrl);
        }
    }

    static int check(URL endpoint, int connectTimeoutMillis, int readTimeoutMillis) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) endpoint.openConnection(Proxy.NO_PROXY);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            return status >= 200 && status < 300 ? 0 : 1;
        } catch (IOException | RuntimeException ignored) {
            return 1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
