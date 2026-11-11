package com.uptime.monitor.checks;

import com.uptime.monitor.config.UptimeProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sends one GET request with a hard deadline covering headers and body.
 * Shared by the HTTP and keyword checkers.
 */
@Component
public class HttpProbe {

    public record Response(int statusCode, int responseTimeMs, String body) {
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final int maxBodyBytes;

    public HttpProbe(UptimeProperties props) {
        this.maxBodyBytes = props.maxBodyBytes();
    }

    /**
     * @param readBody whether to read up to maxBodyBytes of the body; otherwise the body is discarded
     * @throws TimeoutException when the whole exchange takes longer than timeoutMs
     */
    public Response get(String url, int timeoutMs, boolean readBody) throws IOException, TimeoutException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "UptimeMonitor/1.0")
                .GET()
                .build();

        AtomicReference<InputStream> stream = new AtomicReference<>();
        long start = System.nanoTime();
        CompletableFuture<Response> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    int elapsedMs = (int) ((System.nanoTime() - start) / 1_000_000);
                    try (InputStream body = response.body()) {
                        stream.set(body);
                        String text = readBody ? new String(body.readNBytes(maxBodyBytes), StandardCharsets.UTF_8) : null;
                        return new Response(response.statusCode(), elapsedMs, text);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            closeQuietly(stream.get()); // unblocks a slow body read so the thread is freed
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() instanceof RuntimeException r && r.getCause() != null ? r.getCause() : e.getCause();
            if (cause instanceof HttpTimeoutException) {
                // The request's own timeout can win the race against future.get(); report both the same way.
                throw new TimeoutException(cause.getMessage());
            }
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException(cause);
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // already failing; nothing else to do
        }
    }
}
