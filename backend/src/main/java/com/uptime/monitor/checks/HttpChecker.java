package com.uptime.monitor.checks;

import com.uptime.monitor.monitors.CheckType;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;

/** Up when the final response (after redirects) is 2xx or 3xx. */
@Component
public class HttpChecker implements Checker {

    private final HttpProbe probe;

    public HttpChecker(HttpProbe probe) {
        this.probe = probe;
    }

    @Override
    public CheckType type() {
        return CheckType.HTTP;
    }

    @Override
    public CheckOutcome check(CheckTask task) {
        return run(task, false);
    }

    protected CheckOutcome run(CheckTask task, boolean readBody) {
        try {
            HttpProbe.Response response = probe.get(task.url(), task.timeoutMs(), readBody);
            if (response.statusCode() >= 400) {
                return CheckOutcome.down(response.statusCode(), response.responseTimeMs(), "HTTP " + response.statusCode());
            }
            return evaluate(task, response);
        } catch (TimeoutException e) {
            return CheckOutcome.down(null, task.timeoutMs(), "Timed out after " + task.timeoutMs() + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckOutcome.down(null, null, "Check interrupted");
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + e.getMessage();
            return CheckOutcome.down(null, null, message);
        }
    }

    /** Called only for a 2xx or 3xx response. */
    protected CheckOutcome evaluate(CheckTask task, HttpProbe.Response response) {
        return CheckOutcome.up(response.statusCode(), response.responseTimeMs());
    }
}
