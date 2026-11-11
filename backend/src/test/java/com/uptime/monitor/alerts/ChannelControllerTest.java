package com.uptime.monitor.alerts;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"uptime.scheduler-enabled=false", "uptime.alerts.dispatcher-enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChannelControllerTest {

    @Autowired
    MockMvc mvc;

    private ResultActions add(Cookie user, String type, String target) throws Exception {
        return mvc.perform(post("/api/channels").cookie(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"target\":\"" + target + "\"}"));
    }

    @Test
    void newAccountsGetAlertsToTheirEmail() throws Exception {
        String email = TestUsers.uniqueEmail();
        Cookie user = TestUsers.signUp(mvc, email);

        mvc.perform(get("/api/channels").cookie(user))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("EMAIL"))
                .andExpect(jsonPath("$[0].target").value(email));
    }

    @Test
    void validatesTargetsAndRejectsDuplicates() throws Exception {
        Cookie user = TestUsers.signUp(mvc, TestUsers.uniqueEmail());

        add(user, "TELEGRAM", "123456789").andExpect(status().isCreated());
        add(user, "TELEGRAM", "123456789").andExpect(status().isConflict());
        add(user, "TELEGRAM", "@someone").andExpect(status().isBadRequest());
        add(user, "EMAIL", "not-an-email").andExpect(status().isBadRequest());
        add(user, "EMAIL", "Team@Example.com").andExpect(status().isCreated())
                .andExpect(jsonPath("$.target").value("team@example.com"));
    }

    @Test
    void capsChannelsPerUser() throws Exception {
        Cookie user = TestUsers.signUp(mvc, TestUsers.uniqueEmail()); // already has 1
        for (int i = 0; i < 4; i++) {
            add(user, "TELEGRAM", String.valueOf(1000 + i)).andExpect(status().isCreated());
        }
        add(user, "TELEGRAM", "2000").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You can have up to 5 alert channels."));
    }

    @Test
    void testAlertsAreRateLimitedPerChannel() throws Exception {
        Cookie user = TestUsers.signUp(mvc, TestUsers.uniqueEmail());
        long id = Long.parseLong(mvc.perform(get("/api/channels").cookie(user)).andReturn().getResponse()
                .getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(post("/api/channels/{id}/test", id).cookie(user).with(csrf())).andExpect(status().isAccepted());
        mvc.perform(post("/api/channels/{id}/test", id).cookie(user).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        mvc.perform(get("/api/channels").cookie(user))
                .andExpect(jsonPath("$[0].lastDelivery.status").value("PENDING"))
                .andExpect(jsonPath("$[0].lastDelivery.subject").value("Test alert from Uptime"));
    }

    @Test
    void channelsArePrivate() throws Exception {
        Cookie alice = TestUsers.signUp(mvc, TestUsers.uniqueEmail());
        Cookie bob = TestUsers.signUp(mvc, TestUsers.uniqueEmail());
        long id = Long.parseLong(add(alice, "TELEGRAM", "777").andReturn().getResponse()
                .getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(post("/api/channels/{id}/test", id).cookie(bob).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/channels/{id}", id).cookie(bob).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/channels/{id}", id).cookie(alice).with(csrf())).andExpect(status().isNoContent());
    }
}
