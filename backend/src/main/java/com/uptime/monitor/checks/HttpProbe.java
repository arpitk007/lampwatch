package com.uptime.monitor.checks;

import com.uptime.monitor.config.UptimeProperties;
import org.springframework.beans.factory.annotation.Autowired;
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

    public record Response(int statusCode, int responseTimeMs, String body, String location) {
    }

    static final int MAX_REDIRECTS = 5;

    /** Redirects are followed by hand so every hop goes through the URL guard. */
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final int maxBodyBytes;
    private final UrlGuard guard;

    @Autowired
    public HttpProbe(UptimeProperties props, UrlGuard guard) {
        this(props.maxBodyBytes(), guard);
    }

    public HttpProbe(int maxBodyBytes, UrlGuard guard) {
        this.maxBodyBytes = maxBodyBytes;
        this.guard = guard;
    }

    /**
     * Follows up to MAX_REDIRECTS redirects, checking each target with the URL guard.
     * The deadline covers the whole chain; response time is measured across it.
     *
     * @param readBody whether to read up to maxBodyBytes of the final body; otherwise it is discarded
     * @throws TimeoutException when the whole exchange takes longer than timeoutMs
     * @throws UrlGuard.BlockedUrlException when the URL or a redirect points somewhere private
     */
    public Response get(String url, int timeoutMs, boolean readBody)
            throws IOException, TimeoutException, InterruptedException, UrlGuard.BlockedUrlException {
        long start = System.nanoTime();
        long deadline = start + timeoutMs * 1_000_000L;
        URI uri = URI.create(url);
        for (int hop = 0; ; hop++) {
            guard.check(uri);
            long remainingMs = Math.max(1, (deadline - System.nanoTime()) / 1_000_000);
            Response response = fetch(uri, (int) remainingMs, readBody, start);
            String location = response.location();
            if (response.statusCode() / 100 != 3 || location == null) {
                return response;
            }
            if (hop == MAX_REDIRECTS) {
                throw new IOException("Too many redirects");
            }
            uri = uri.resolve(location);
        }
    }

    private Response fetch(URI uri, int timeoutMs, boolean readBody, long start)
            throws IOException, TimeoutException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "UptimeMonitor/1.0")
                .GET()
                .build();

        AtomicReference<InputStream> stream = new AtomicReference<>();
        CompletableFuture<Response> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    int elapsedMs = (int) ((System.nanoTime() - start) / 1_000_000);
                    try (InputStream body = response.body()) {
                        stream.set(body);
                        boolean redirect = response.statusCode() / 100 == 3;
                        String text = readBody && !redirect ? new String(body.readNBytes(maxBodyBytes), StandardCharsets.UTF_8) : null;
                        return new Response(response.statusCode(), elapsedMs, text,
                                response.headers().firstValue("Location").orElse(null));
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
