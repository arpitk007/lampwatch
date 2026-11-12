package com.uptime.monitor.common;

import com.uptime.monitor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a real Tomcat, because forwarded-header handling (server.forward-headers-strategy=native) is a
 * Tomcat valve that MockMvc never reaches. Requests come from loopback, which the valve treats as a
 * trusted proxy, like Caddy on the private Docker network in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "uptime.scheduler-enabled=false", "uptime.alerts.dispatcher-enabled=false", "uptime.worker-enabled=false",
        "uptime.rate-limit.enabled=true", "uptime.rate-limit.per-ip-per-minute=3"})
@Import(TestcontainersConfiguration.class)
class RateLimiterForwardedTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private int get(String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/public/status-pages/nope"))
                .header("X-Forwarded-For", forwardedFor).build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    @Test
    void budgetsFollowTheClientBehindTheProxyNotTheProxy() throws Exception {
        String client = randomIp();
        for (int i = 0; i < 3; i++) assertThat(get(client)).isEqualTo(404);
        assertThat(get(client)).isEqualTo(429);

        // Another visitor arriving through the same proxy is unaffected.
        assertThat(get(randomIp())).isEqualTo(404);
    }
}
