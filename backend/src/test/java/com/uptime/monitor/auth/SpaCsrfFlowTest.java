package com.uptime.monitor.auth;

import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The browser flow against a real server, the way Angular does it:
 * read the XSRF-TOKEN cookie from GET /me, then echo it in the X-XSRF-TOKEN header.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "uptime.scheduler-enabled=false")
@Import(TestcontainersConfiguration.class)
class SpaCsrfFlowTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String cookieValue(HttpResponse<?> response, String name) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        return cookies.stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(name.length() + 1, c.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + name + " cookie in " + cookies));
    }

    private HttpRequest.Builder signUp(String xsrfCookie, String xsrfHeader) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/auth/signup"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + xsrfCookie)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + TestUsers.uniqueEmail() + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}"));
        return xsrfHeader == null ? request : request.header("X-XSRF-TOKEN", xsrfHeader);
    }

    @Test
    void cookieFromMeLetsTheSpaPost() throws Exception {
        HttpResponse<String> me = send(HttpRequest.newBuilder(uri("/api/auth/me")));
        assertThat(me.statusCode()).isEqualTo(401);
        String token = cookieValue(me, "XSRF-TOKEN");

        HttpResponse<String> created = send(signUp(token, token));
        assertThat(created.statusCode()).isEqualTo(201);
        String session = cookieValue(created, "SESSION");

        HttpResponse<String> meAgain = send(HttpRequest.newBuilder(uri("/api/auth/me")).header("Cookie", "SESSION=" + session));
        assertThat(meAgain.statusCode()).isEqualTo(200);
        assertThat(cookieValue(created, "SESSION")).isNotBlank();
        assertThat(created.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("SESSION=")).findFirst().orElseThrow())
                .contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void postWithCookieButNoHeaderIsForbidden() throws Exception {
        HttpResponse<String> me = send(HttpRequest.newBuilder(uri("/api/auth/me")));
        String token = cookieValue(me, "XSRF-TOKEN");

        assertThat(send(signUp(token, null)).statusCode()).isEqualTo(403);
        assertThat(send(signUp(token, "forged-" + token)).statusCode()).isEqualTo(403);
    }
}
