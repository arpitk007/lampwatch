package com.uptime.monitor.monitors;

import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"uptime.scheduler-enabled=false", "uptime.max-monitors-per-user=3"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MonitorControllerTest {

    private static final String HTTP_MONITOR = """
            {"name": "Example", "url": "https://example.com", "checkType": "HTTP",
             "intervalSeconds": 60, "timeoutMs": 5000}
            """;

    @Autowired
    MockMvc mvc;

    Cookie alice;
    Cookie bob;

    @BeforeEach
    void signUp() throws Exception {
        alice = TestUsers.signUp(mvc, TestUsers.uniqueEmail());
        bob = TestUsers.signUp(mvc, TestUsers.uniqueEmail());
    }

    private long create(Cookie user) throws Exception {
        String body = mvc.perform(post("/api/monitors").cookie(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(HTTP_MONITOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void requiresSignIn() throws Exception {
        mvc.perform(get("/api/monitors")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsAndReadsMonitor() throws Exception {
        long id = create(alice);

        mvc.perform(get("/api/monitors/{id}", id).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.status").value("UNKNOWN"));
        mvc.perform(get("/api/monitors").cookie(alice)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void updatesPausesAndDeletes() throws Exception {
        long id = create(alice);
        String renamed = HTTP_MONITOR.replace("\"Example\"", "\"Renamed\"");

        mvc.perform(put("/api/monitors/{id}", id).cookie(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(renamed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
        mvc.perform(post("/api/monitors/{id}/pause", id).cookie(alice).with(csrf())).andExpect(jsonPath("$.paused").value(true));
        mvc.perform(post("/api/monitors/{id}/resume", id).cookie(alice).with(csrf())).andExpect(jsonPath("$.paused").value(false));
        mvc.perform(delete("/api/monitors/{id}", id).cookie(alice).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/monitors/{id}", id).cookie(alice)).andExpect(status().isNotFound());
    }

    @Test
    void usersCannotSeeOrChangeEachOthersMonitors() throws Exception {
        long id = create(alice);

        mvc.perform(get("/api/monitors").cookie(bob)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/monitors/{id}", id).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/monitors/{id}/checks", id).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/monitors/{id}/incidents", id).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/monitors/{id}/incidents", id).cookie(alice)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(put("/api/monitors/{id}", id).cookie(bob).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(HTTP_MONITOR)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/monitors/{id}", id).cookie(bob).with(csrf())).andExpect(status().isNotFound());

        mvc.perform(get("/api/monitors/{id}", id).cookie(alice)).andExpect(status().isOk());
    }

    @Test
    void capsMonitorsPerUser() throws Exception {
        create(alice);
        create(alice);
        create(alice);
        mvc.perform(post("/api/monitors").cookie(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(HTTP_MONITOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You can have up to 3 monitors. Delete one to add another."));
        create(bob); // the cap is per user
    }

    @Test
    void rejectsUrlsThatPointIntoPrivateNetworks() throws Exception {
        for (String url : new String[]{"http://127.0.0.1:8080/admin", "http://169.254.169.254/latest/meta-data/",
                "http://localhost/", "http://10.0.0.5/", "http://[::1]/", "http://user:secret@example.com/"}) {
            mvc.perform(post("/api/monitors").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(HTTP_MONITOR.replace("https://example.com", url)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/monitors").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "", "url": "ftp://x", "checkType": "HTTP", "intervalSeconds": 10, "timeoutMs": 5000}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists())
                .andExpect(jsonPath("$.errors.intervalSeconds").exists());

        mvc.perform(post("/api/monitors").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Kw", "url": "https://example.com", "checkType": "KEYWORD", "intervalSeconds": 60, "timeoutMs": 5000}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("keyword is required for KEYWORD checks"));
    }
}
