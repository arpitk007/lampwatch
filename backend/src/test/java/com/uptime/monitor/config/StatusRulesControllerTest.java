package com.uptime.monitor.config;

import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"uptime.scheduler-enabled=false", "uptime.alerts.dispatcher-enabled=false", "uptime.degraded-after-ms=2500"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StatusRulesControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void reportsTheConfiguredThresholds() throws Exception {
        Cookie user = TestUsers.signUp(mvc, TestUsers.uniqueEmail());

        mvc.perform(get("/api/settings/status-rules").cookie(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slowAfterMs").value(2500))
                .andExpect(jsonPath("$.failuresToDown").value(3))
                .andExpect(jsonPath("$.passesToRecover").value(2));
    }

    @Test
    void requiresSignIn() throws Exception {
        mvc.perform(get("/api/settings/status-rules")).andExpect(status().isUnauthorized());
    }
}
