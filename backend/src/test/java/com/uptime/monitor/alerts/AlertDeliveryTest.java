package com.uptime.monitor.alerts;

import com.sun.net.httpserver.HttpServer;
import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.checks.CheckOutcome;
import com.uptime.monitor.checks.CheckResultRecorder;
import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.monitors.CheckType;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import com.uptime.monitor.users.User;
import com.uptime.monitor.users.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real email through Mailpit and a stand-in Telegram API, driven by real incidents
 * from the check recorder. The dispatcher is invoked by hand to control timing.
 */
@SpringBootTest(properties = {
        "uptime.scheduler-enabled=false",
        "uptime.worker-enabled=false",
        "uptime.alerts.poll-ms=3600000",
        "uptime.alerts.max-attempts=2"})
@Import(TestcontainersConfiguration.class)
class AlertDeliveryTest {

    static final GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.21"))
            .withExposedPorts(1025, 8025);
    static final FakeTelegram telegram = new FakeTelegram();

    static {
        mailpit.start();
    }

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        registry.add("uptime.alerts.telegram-api-base", telegram::baseUrl);
        registry.add("uptime.alerts.telegram-bot-token", () -> "test-token");
    }

    @AfterAll
    static void stop() {
        telegram.close();
        mailpit.stop();
    }

    static final CheckOutcome FAIL = CheckOutcome.down(503, 10, "HTTP 503");
    static final CheckOutcome PASS = CheckOutcome.up(200, 40);

    @Autowired CheckResultRecorder recorder;
    @Autowired AlertDispatcher dispatcher;
    @Autowired MonitorRepository monitors;
    @Autowired UserRepository users;
    @Autowired NotificationChannelRepository channels;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;

    String email;
    long monitorId;

    @BeforeEach
    void setUp() throws Exception {
        monitors.deleteAll();
        jdbc.sql("DELETE FROM notification_outbox").update();
        telegram.reset();
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/messages"))).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());

        email = TestUsers.uniqueEmail();
        long owner = users.save(new User(email, "{noop}unused")).getId();
        channels.save(new NotificationChannel(owner, ChannelType.EMAIL, email));
        channels.save(new NotificationChannel(owner, ChannelType.TELEGRAM, "424242"));
        monitorId = monitors.save(new Monitor(owner, "Shop", "https://shop.example.com", CheckType.HTTP, null, 60, 5000)).getId();
    }

    private void check(CheckOutcome... outcomes) {
        for (CheckOutcome outcome : outcomes) {
            recorder.record(new CheckTask(monitorId, "https://shop.example.com", CheckType.HTTP, null, 5000), outcome, Instant.now());
        }
    }

    private static String mailpitApi(String path) {
        return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path;
    }

    private static String mailpitMessages() throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/messages"))).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private List<String> outboxStatuses() {
        return jdbc.sql("SELECT CONCAT(kind, ':', status) FROM notification_outbox ORDER BY id").query(String.class).list();
    }

    @Test
    void outageAndRecoveryAlertEveryChannelOnce() throws Exception {
        check(FAIL, FAIL, FAIL, FAIL);
        assertThat(outboxStatuses()).containsExactly("OPENED:PENDING", "OPENED:PENDING");

        dispatcher.dispatch();
        assertThat(outboxStatuses()).containsExactly("OPENED:SENT", "OPENED:SENT");
        assertThat(mailpitMessages()).contains("Down: Shop").contains(email);
        assertThat(telegram.bodies).singleElement().satisfies(b -> {
            assertThat(b).contains("\"chat_id\":\"424242\"").contains("Down: Shop").contains("HTTP 503");
        });
        assertThat(telegram.paths).containsExactly("/bottest-token/sendMessage");

        check(PASS, PASS);
        dispatcher.dispatch();
        assertThat(outboxStatuses()).containsExactly("OPENED:SENT", "OPENED:SENT", "RESOLVED:SENT", "RESOLVED:SENT");
        assertThat(mailpitMessages()).contains("Back up: Shop");

        dispatcher.dispatch(); // nothing left to send
        assertThat(telegram.bodies).hasSize(2);
    }

    @Test
    void rolledBackIncidentLeavesNoAlert() {
        tx.executeWithoutResult(status -> {
            check(FAIL, FAIL, FAIL);
            status.setRollbackOnly();
        });
        assertThat(outboxStatuses()).isEmpty();
    }

    @Test
    void failedDeliveryBacksOffThenGivesUp() {
        telegram.failWith(500);
        check(FAIL, FAIL, FAIL);

        dispatcher.dispatch();
        record Row(String type, String status, int attempts, Instant next, String error) {
        }
        Row telegramRow = jdbc.sql("""
                        SELECT c.type, o.status, o.attempts, o.next_attempt_at, o.last_error
                        FROM notification_outbox o JOIN notification_channels c ON c.id = o.channel_id
                        WHERE c.type = 'TELEGRAM'""")
                .query((rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getObject(4, java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC), rs.getString(5)))
                .single();
        assertThat(telegramRow.status()).isEqualTo("PENDING");
        assertThat(telegramRow.attempts()).isEqualTo(1);
        assertThat(telegramRow.next()).isAfter(Instant.now().plusSeconds(20)); // first backoff is 30 s
        assertThat(telegramRow.error()).contains("500");

        jdbc.sql("UPDATE notification_outbox SET next_attempt_at = UTC_TIMESTAMP(6) WHERE status = 'PENDING'").update();
        dispatcher.dispatch();
        assertThat(outboxStatuses()).containsExactlyInAnyOrder("OPENED:SENT", "OPENED:FAILED");
    }

    @Test
    void backoffGrowsFourfoldAndCapsAtAnHour() {
        assertThat(AlertDispatcher.backoff(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(AlertDispatcher.backoff(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(AlertDispatcher.backoff(3)).isEqualTo(Duration.ofMinutes(8));
        assertThat(AlertDispatcher.backoff(5)).isEqualTo(Duration.ofHours(1));
    }

    /** Minimal stand-in for api.telegram.org that records requests and can be told to fail. */
    static final class FakeTelegram implements AutoCloseable {
        final List<String> bodies = new CopyOnWriteArrayList<>();
        final List<String> paths = new CopyOnWriteArrayList<>();
        private volatile int status = 200;
        private final HttpServer server;

        FakeTelegram() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/", ex -> {
                paths.add(ex.getRequestURI().getPath());
                bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] reply = (status == 200 ? "{\"ok\":true}" : "{\"ok\":false}").getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(status, reply.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(reply);
                }
            });
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void failWith(int code) {
            status = code;
        }

        void reset() {
            bodies.clear();
            paths.clear();
            status = 200;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
