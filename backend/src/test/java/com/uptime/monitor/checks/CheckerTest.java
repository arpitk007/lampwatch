package com.uptime.monitor.checks;

import com.uptime.monitor.TestHttpServer;
import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.monitors.CheckType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CheckerTest {

    static TestHttpServer server;
    static HttpChecker http;
    static KeywordChecker keyword;

    @BeforeAll
    static void start() throws IOException {
        server = new TestHttpServer();
        HttpProbe probe = new HttpProbe(new UptimeProperties(50, 8, 60_000, 3, 300_000, 3, 2, 3000, 1_048_576, 20));
        http = new HttpChecker(probe);
        keyword = new KeywordChecker(probe);
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    private static CheckTask task(String path, CheckType type, String kw, int timeoutMs) {
        return new CheckTask(1, server.url(path), type, kw, timeoutMs);
    }

    @Test
    void okResponseIsUp() {
        CheckOutcome outcome = http.check(task("/ok", CheckType.HTTP, null, 2000));
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(200);
        assertThat(outcome.responseTimeMs()).isNotNull();
    }

    @Test
    void redirectIsFollowed() {
        assertThat(http.check(task("/redirect", CheckType.HTTP, null, 2000)).statusCode()).isEqualTo(200);
    }

    @Test
    void serverErrorIsDown() {
        CheckOutcome outcome = http.check(task("/error", CheckType.HTTP, null, 2000));
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorMessage()).isEqualTo("HTTP 500");
    }

    @RepeatedTest(5)
    void slowResponseTimesOutOnDeadline() {
        long start = System.nanoTime();
        CheckOutcome outcome = http.check(task("/slow", CheckType.HTTP, null, 1000));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorMessage()).startsWith("Timed out");
        assertThat(elapsedMs).isLessThan(2500);
    }

    @Test
    void refusedConnectionIsDown() {
        CheckOutcome outcome = http.check(new CheckTask(1, "http://127.0.0.1:1/", CheckType.HTTP, null, 2000));
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorMessage()).isNotBlank();
    }

    @Test
    void keywordPresentIsUpAndMissingIsDown() {
        assertThat(keyword.check(task("/ok", CheckType.KEYWORD, "world", 2000)).success()).isTrue();
        CheckOutcome missing = keyword.check(task("/ok", CheckType.KEYWORD, "absent", 2000));
        assertThat(missing.success()).isFalse();
        assertThat(missing.errorMessage()).isEqualTo("Keyword not found: absent");
    }
}
