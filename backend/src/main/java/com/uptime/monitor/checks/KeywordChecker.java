package com.uptime.monitor.checks;

import com.uptime.monitor.monitors.CheckType;
import org.springframework.stereotype.Component;

/** Up when the response is 2xx or 3xx and the body contains the keyword. */
@Component
public class KeywordChecker extends HttpChecker {

    public KeywordChecker(HttpProbe probe) {
        super(probe);
    }

    @Override
    public CheckType type() {
        return CheckType.KEYWORD;
    }

    @Override
    public CheckOutcome check(CheckTask task) {
        return run(task, true);
    }

    @Override
    protected CheckOutcome evaluate(CheckTask task, HttpProbe.Response response) {
        if (response.body() != null && response.body().contains(task.keyword())) {
            return CheckOutcome.up(response.statusCode(), response.responseTimeMs());
        }
        return CheckOutcome.down(response.statusCode(), response.responseTimeMs(), "Keyword not found: " + task.keyword());
    }
}
