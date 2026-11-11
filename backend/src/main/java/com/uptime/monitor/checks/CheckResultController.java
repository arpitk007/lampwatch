package com.uptime.monitor.checks;

import com.uptime.monitor.common.NotFoundException;
import com.uptime.monitor.monitors.MonitorRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CheckResultController {

    private final CheckResultRepository results;
    private final MonitorRepository monitors;

    public CheckResultController(CheckResultRepository results, MonitorRepository monitors) {
        this.results = results;
        this.monitors = monitors;
    }

    @GetMapping("/api/monitors/{id}/checks")
    public List<CheckResultResponse> recent(@PathVariable long id,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        if (!monitors.existsById(id)) {
            throw new NotFoundException("Monitor " + id + " not found");
        }
        return results.findByMonitorIdOrderByCheckedAtDesc(id, PageRequest.of(0, limit)).stream()
                .map(CheckResultResponse::from)
                .toList();
    }
}
