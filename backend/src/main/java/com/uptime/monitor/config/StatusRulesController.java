package com.uptime.monitor.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The thresholds behind Up, Slow and Down, so the UI can explain them instead of hard-coding copies. */
@RestController
public class StatusRulesController {

    public record StatusRules(int slowAfterMs, int failuresToDown, int passesToRecover) {
    }

    private final StatusRules rules;

    public StatusRulesController(UptimeProperties props) {
        this.rules = new StatusRules(props.degradedAfterMs(), props.failureThreshold(), props.recoveryThreshold());
    }

    @GetMapping("/api/settings/status-rules")
    public StatusRules rules() {
        return rules;
    }
}
