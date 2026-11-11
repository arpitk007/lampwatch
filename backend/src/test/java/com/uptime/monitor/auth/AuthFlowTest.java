package com.uptime.monitor.auth;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "uptime.scheduler-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String email, String password) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(json(post("/api/auth/login").with(csrf()), email, password)).andReturn();
    }

    @Test
    void signUpSignsInAndNormalizesEmail() throws Exception {
        String email = TestUsers.uniqueEmail();
        Cookie session = TestUsers.signUp(mvc, "  " + email.toUpperCase() + " ");

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = TestUsers.uniqueEmail();
        TestUsers.signUp(mvc, email);
        mvc.perform(json(post("/api/auth/signup").with(csrf()), email, TestUsers.PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mvc.perform(json(post("/api/auth/signup").with(csrf()), TestUsers.uniqueEmail(), "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("must be 10 to 72 characters"));
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(json(post("/api/auth/login"), TestUsers.uniqueEmail(), TestUsers.PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String email = TestUsers.uniqueEmail();
        TestUsers.signUp(mvc, email);

        MvcResult wrong = login(email, "not-the-password");
        MvcResult unknown = login(TestUsers.uniqueEmail(), "not-the-password");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
    }

    @Test
    void loginIssuesNewSessionId() throws Exception {
        String email = TestUsers.uniqueEmail();
        Cookie before = TestUsers.signUp(mvc, email);

        MvcResult result = mvc.perform(json(post("/api/auth/login").with(csrf()).cookie(before), email, TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(TestUsers.session(result).getValue()).isNotEqualTo(before.getValue());
    }

    @Test
    void fiveFailuresLockTheEmailEvenForTheRightPassword() throws Exception {
        String email = TestUsers.uniqueEmail();
        TestUsers.signUp(mvc, email);
        for (int i = 0; i < LoginThrottle.MAX_PER_EMAIL; i++) {
            assertThat(login(email, "wrong-password-" + i).getResponse().getStatus()).isEqualTo(401);
        }

        mvc.perform(json(post("/api/auth/login").with(csrf()), email, TestUsers.PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        Cookie session = TestUsers.signUp(mvc, TestUsers.uniqueEmail());

        mvc.perform(post("/api/auth/logout").cookie(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/monitors").cookie(session)).andExpect(status().isUnauthorized());
    }
}
