package com.uptime.monitor.common;

import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "uptime.scheduler-enabled=false", "uptime.alerts.dispatcher-enabled=false",
        "uptime.rate-limit.enabled=true", "uptime.rate-limit.per-user-per-minute=5",
        "uptime.rate-limit.per-ip-per-minute=4", "uptime.rate-limit.signups-per-ip-per-hour=2"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimiterTest {

    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** A fresh documentation-range address, so tests and reruns within a window don't share budgets. */
    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.51." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    @Test
    void anonymousCallersGetAPerIpBudgetThen429WithRetryAfter() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 4; i++) {
            mvc.perform(get("/api/public/status-pages/nope").with(from(ip))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/public/status-pages/nope").with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Too many requests from this network. Try again in a minute."));

        // Another address has its own budget.
        mvc.perform(get("/api/public/status-pages/nope").with(from(randomIp()))).andExpect(status().isNotFound());
    }

    @Test
    void signedInUsersAreCountedPerAccountNotPerAddress() throws Exception {
        Cookie user = TestUsers.signUp(mvc, TestUsers.uniqueEmail());
        for (int i = 0; i < 5; i++) {
            // A different address each time: the budget still follows the account.
            mvc.perform(get("/api/monitors").cookie(user).with(from(randomIp()))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/monitors").cookie(user).with(from(randomIp()))).andExpect(status().isTooManyRequests());
    }

    @Test
    void limitsNewAccountsPerAddress() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 2; i++) {
            signUp(ip).andExpect(status().isCreated());
        }
        signUp(ip).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Too many new accounts from this network. Try again later."));
    }

    private ResultActions signUp(String ip) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(csrf()).with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + TestUsers.uniqueEmail() + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}"));
    }
}
