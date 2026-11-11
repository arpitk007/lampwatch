package com.uptime.monitor;

import com.uptime.monitor.users.User;
import com.uptime.monitor.users.UserRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Test accounts always use example.com addresses. */
public final class TestUsers {

    public static final String PASSWORD = "correct-horse-battery";

    private TestUsers() {
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** A user row for tests that don't go through HTTP. */
    public static long create(UserRepository users) {
        return users.save(new User(uniqueEmail(), "{noop}unused")).getId();
    }

    /** Signs up through the API and returns the session cookie. */
    public static Cookie signUp(MockMvc mvc, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Sign-up failed: " + result.getResponse().getContentAsString());
        }
        return session(result);
    }

    public static Cookie session(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) {
            throw new IllegalStateException("No SESSION cookie in response");
        }
        return cookie;
    }
}
