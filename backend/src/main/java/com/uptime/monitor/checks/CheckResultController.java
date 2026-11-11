package com.uptime.monitor.checks;

import com.uptime.monitor.common.NotFoundException;
import com.uptime.monitor.monitors.MonitorRepository;
import com.uptime.monitor.users.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
public class CheckResultController {

    private final CheckResultRepository results;
    private final MonitorRepository monitors;
    private final HeartbeatQuery heartbeats;
    private final JdbcClient jdbc;

    public CheckResultController(CheckResultRepository results, MonitorRepository monitors, HeartbeatQuery heartbeats, JdbcClient jdbc) {
        this.results = results;
        this.monitors = monitors;
        this.heartbeats = heartbeats;
        this.jdbc = jdbc;
    }

    /** One point per hour with checks. avgMs is null when no check that hour succeeded. */
    public record HourPoint(Instant hour, Integer avgMs, int checks, int failures) {
    }

    /** Hourly average response time from the rollups, oldest first. Hours without checks are absent. */
    @GetMapping("/api/monitors/{id}/response-times")
    public List<HourPoint> responseTimes(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                         @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
        if (!monitors.existsByIdAndOwnerId(id, user.id())) {
            throw new NotFoundException("Monitor " + id + " not found");
        }
        LocalDateTime since = LocalDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.HOURS), ZoneOffset.UTC).minusHours(hours - 1L);
        return jdbc.sql("""
                        SELECT hour_start, response_ms_total, response_count, checks, failures
                        FROM check_rollups_hourly
                        WHERE monitor_id = :id AND hour_start >= :since
                        ORDER BY hour_start""")
                .param("id", id)
                .param("since", since)
                .query((rs, n) -> {
                    int timed = rs.getInt("response_count");
                    return new HourPoint(rs.getObject("hour_start", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                            timed == 0 ? null : (int) Math.round(rs.getLong("response_ms_total") / (double) timed),
                            rs.getInt("checks"), rs.getInt("failures"));
                })
                .list();
    }

    /** Latest results for all monitors at once, keyed by monitor id, oldest first. */
    @GetMapping("/api/monitors/heartbeats")
    public Map<Long, List<CheckResultResponse>> heartbeats(@AuthenticationPrincipal AuthUser user,
                                                         @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return heartbeats.latest(user.id(), limit);
    }

    @GetMapping("/api/monitors/{id}/checks")
    public List<CheckResultResponse> recent(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        if (!monitors.existsByIdAndOwnerId(id, user.id())) {
            throw new NotFoundException("Monitor " + id + " not found");
        }
        return results.findByMonitorIdOrderByCheckedAtDesc(id, PageRequest.of(0, limit)).stream()
                .map(CheckResultResponse::from)
                .toList();
    }
}
