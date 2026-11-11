package com.uptime.monitor.monitors;

import com.uptime.monitor.users.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {

    private final MonitorService service;

    public MonitorController(MonitorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody MonitorRequest req) {
        return service.create(user.id(), req);
    }

    @GetMapping
    public List<MonitorResponse> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user.id());
    }

    @GetMapping("/{id}")
    public MonitorResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return service.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public MonitorResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable long id, @Valid @RequestBody MonitorRequest req) {
        return service.update(user.id(), id, req);
    }

    @PostMapping("/{id}/pause")
    public MonitorResponse pause(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return service.setPaused(user.id(), id, true);
    }

    @PostMapping("/{id}/resume")
    public MonitorResponse resume(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return service.setPaused(user.id(), id, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        service.delete(user.id(), id);
    }
}
