package com.uptime.monitor.monitors;

import com.uptime.monitor.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "uptime.scheduler-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MonitorControllerTest {

    private static final String HTTP_MONITOR = """
            {"name": "Example", "url": "https://example.com", "checkType": "HTTP",
             "intervalSeconds": 60, "timeoutMs": 5000}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    MonitorRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsAndReadsMonitor() throws Exception {
        String body = mvc.perform(post("/api/monitors").contentType(MediaType.APPLICATION_JSON).content(HTTP_MONITOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.paused").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(get("/api/monitors/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com"));
        mvc.perform(get("/api/monitors")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void updatesPausesAndDeletes() throws Exception {
        long id = repository.save(new Monitor("Old", "https://old.example", CheckType.HTTP, null, 60, 5000)).getId();

        mvc.perform(put("/api/monitors/{id}", id).contentType(MediaType.APPLICATION_JSON).content(HTTP_MONITOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Example"));
        mvc.perform(post("/api/monitors/{id}/pause", id)).andExpect(jsonPath("$.paused").value(true));
        mvc.perform(post("/api/monitors/{id}/resume", id)).andExpect(jsonPath("$.paused").value(false));
        mvc.perform(delete("/api/monitors/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/monitors/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/monitors").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "", "url": "ftp://x", "checkType": "HTTP", "intervalSeconds": 10, "timeoutMs": 5000}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists())
                .andExpect(jsonPath("$.errors.intervalSeconds").exists());

        mvc.perform(post("/api/monitors").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Kw", "url": "https://example.com", "checkType": "KEYWORD", "intervalSeconds": 60, "timeoutMs": 5000}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("keyword is required for KEYWORD checks"));
    }
}
