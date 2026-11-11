package com.uptime.monitor.incidents;

import com.uptime.monitor.common.NotFoundException;
import com.uptime.monitor.monitors.MonitorRepository;
import com.uptime.monitor.users.AuthUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IncidentController {

    private final IncidentRepository incidents;
    private final MonitorRepository monitors;

    public IncidentController(IncidentRepository incidents, MonitorRepository monitors) {
        this.incidents = incidents;
        this.monitors = monitors;
    }

    @GetMapping("/api/monitors/{id}/incidents")
    public List<IncidentResponse> recent(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        if (!monitors.existsByIdAndOwnerId(id, user.id())) {
            throw new NotFoundException("Monitor " + id + " not found");
        }
        return incidents.findByMonitorIdOrderByStartedAtDesc(id, PageRequest.of(0, limit)).stream()
                .map(IncidentResponse::from)
                .toList();
    }
}
