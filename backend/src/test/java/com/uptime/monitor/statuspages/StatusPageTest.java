package com.uptime.monitor.statuspages;

import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.checks.CheckOutcome;
import com.uptime.monitor.checks.CheckResultRecorder;
import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.monitors.CheckType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"uptime.scheduler-enabled=false", "uptime.worker-enabled=false", "uptime.alerts.dispatcher-enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StatusPageTest {

    @Autowired MockMvc mvc;
    @Autowired CheckResultRecorder recorder;
    @Autowired JdbcClient jdbc;

    Cookie owner;
    String slug;
    long shop;
    long blog;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestUsers.signUp(mvc, TestUsers.uniqueEmail());
        slug = "acme-" + UUID.randomUUID().toString().substring(0, 8);
        shop = createMonitor(owner, "Shop", "https://internal-shop.corp.example");
        blog = createMonitor(owner, "Blog", "https://blog.example");
    }

    private long createMonitor(Cookie user, String name, String url) throws Exception {
        String body = mvc.perform(post("/api/monitors").cookie(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"url\":\"" + url + "\",\"checkType\":\"HTTP\",\"intervalSeconds\":60,\"timeoutMs\":5000}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions save(Cookie user, String pageSlug, String title, long... ids) throws Exception {
        StringBuilder list = new StringBuilder();
        for (long id : ids) list.append(list.isEmpty() ? "" : ",").append(id);
        return mvc.perform(put("/api/status-page").cookie(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + pageSlug + "\",\"title\":\"" + title + "\",\"monitorIds\":[" + list + "]}"));
    }

    private void check(long monitorId, CheckOutcome outcome) {
        recorder.record(new CheckTask(monitorId, "https://x", CheckType.HTTP, null, 5000), outcome, Instant.now());
    }

    @Test
    void publicPageNeedsNoSignInAndHidesUrlsAndErrors() throws Exception {
        save(owner, slug, "Acme status", shop, blog).andExpect(status().isOk());
        check(shop, CheckOutcome.down(500, 5, "Connection refused to 10.0.0.12"));

        String body = mvc.perform(get("/api/public/status-pages/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=30, public"))
                .andExpect(jsonPath("$.title").value("Acme status"))
                .andExpect(jsonPath("$.monitors", hasSize(2)))
                .andExpect(jsonPath("$.monitors[0].name").value("Shop"))
                .andExpect(jsonPath("$.monitors[0].days", hasSize(PublicStatusQuery.DAYS)))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("internal-shop").doesNotContain("10.0.0.12").doesNotContain("\"url\"");
    }

    @Test
    void uptimeComesFromHourlyRollups() throws Exception {
        check(shop, CheckOutcome.up(200, 100));
        check(shop, CheckOutcome.up(200, 120));
        check(shop, CheckOutcome.up(200, 110));
        check(shop, CheckOutcome.down(503, 50, "HTTP 503"));
        save(owner, slug, "Acme", shop).andExpect(status().isOk());

        mvc.perform(get("/api/public/status-pages/{slug}", slug))
                .andExpect(jsonPath("$.monitors[0].uptime24h").value(75.0))
                .andExpect(jsonPath("$.monitors[0].uptime30d").value(75.0))
                .andExpect(jsonPath("$.monitors[0].days[29].uptime").value(75.0))
                .andExpect(jsonPath("$.monitors[0].days[0].uptime").value(nullValue()));
    }

    @Test
    void anyDowntimeShowsBelowOneHundredPercent() throws Exception {
        jdbc.sql("INSERT INTO check_rollups_hourly VALUES (:m, :h, 100000, 1, 0, 0)")
                .param("m", shop).param("h", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0))
                .update();
        save(owner, slug, "Acme", shop).andExpect(status().isOk());
        mvc.perform(get("/api/public/status-pages/{slug}", slug)).andExpect(jsonPath("$.monitors[0].uptime30d").value(99.99));
    }

    @Test
    void responseTimesAverageSuccessfulChecksPerHour() throws Exception {
        check(shop, CheckOutcome.up(200, 100));
        check(shop, CheckOutcome.up(200, 120));
        check(shop, CheckOutcome.down(503, 9000, "HTTP 503")); // failures don't skew the average
        mvc.perform(get("/api/monitors/{id}/response-times", shop).param("hours", "24").cookie(owner))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].avgMs").value(110))
                .andExpect(jsonPath("$[0].checks").value(3))
                .andExpect(jsonPath("$[0].failures").value(1));
        Cookie stranger = TestUsers.signUp(mvc, TestUsers.uniqueEmail());
        mvc.perform(get("/api/monitors/{id}/response-times", shop).cookie(stranger)).andExpect(status().isNotFound());
    }

    @Test
    void redeliveredCheckCountsOnceInRollups() {
        CheckTask task = new CheckTask(shop, "https://x", CheckType.HTTP, null, 5000);
        recorder.record(task, CheckOutcome.up(200, 100), Instant.now(), "job-" + shop);
        recorder.record(task, CheckOutcome.up(200, 100), Instant.now(), "job-" + shop);

        Integer checks = jdbc.sql("SELECT SUM(checks) FROM check_rollups_hourly WHERE monitor_id = :m")
                .param("m", shop).query(Integer.class).single();
        assertThat(checks).isEqualTo(1);
    }

    @Test
    void overallReflectsWorstActiveMonitor() throws Exception {
        save(owner, slug, "Acme", shop, blog).andExpect(status().isOk());
        check(blog, CheckOutcome.up(200, 100));
        for (int i = 0; i < 3; i++) check(shop, CheckOutcome.down(503, 50, "HTTP 503"));
        save(owner, slug, "Acme", shop, blog); // evict the cached copy

        mvc.perform(get("/api/public/status-pages/{slug}", slug))
                .andExpect(jsonPath("$.overall").value("PARTIAL_OUTAGE"))
                .andExpect(jsonPath("$.incidents", hasSize(1)))
                .andExpect(jsonPath("$.incidents[0].monitorName").value("Shop"));
    }

    @Test
    void servesFromCacheUntilThePageIsSaved() throws Exception {
        save(owner, slug, "Acme", shop).andExpect(status().isOk());
        mvc.perform(get("/api/public/status-pages/{slug}", slug)).andExpect(jsonPath("$.monitors[0].name").value("Shop"));

        jdbc.sql("UPDATE monitors SET name = 'Storefront' WHERE id = :id").param("id", shop).update();
        mvc.perform(get("/api/public/status-pages/{slug}", slug)).andExpect(jsonPath("$.monitors[0].name").value("Shop"));

        save(owner, slug, "Acme", shop).andExpect(status().isOk());
        mvc.perform(get("/api/public/status-pages/{slug}", slug)).andExpect(jsonPath("$.monitors[0].name").value("Storefront"));
    }

    @Test
    void validatesSlugOwnershipAndMonitors() throws Exception {
        save(owner, slug, "Acme", shop).andExpect(status().isOk());
        Cookie other = TestUsers.signUp(mvc, TestUsers.uniqueEmail());

        save(other, slug, "Mine now", new long[0]).andExpect(status().isConflict());
        save(other, "other-" + slug, "Mine", shop).andExpect(status().isBadRequest());
        save(owner, "-bad-", "Acme", shop).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").exists());
        mvc.perform(get("/api/public/status-pages/{slug}", "no-such-page")).andExpect(status().isNotFound());
        mvc.perform(get("/api/status-page").cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/status-page").cookie(owner)).andExpect(jsonPath("$.monitorIds[0]").value(shop));
    }
}
