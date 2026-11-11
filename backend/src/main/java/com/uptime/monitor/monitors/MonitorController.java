package com.uptime.monitor.monitors;

import jakarta.validation.Valid;
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
    public MonitorResponse create(@Valid @RequestBody MonitorRequest req) {
        return service.create(req);
    }

    @GetMapping
    public List<MonitorResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public MonitorResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public MonitorResponse update(@PathVariable long id, @Valid @RequestBody MonitorRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/pause")
    public MonitorResponse pause(@PathVariable long id) {
        return service.setPaused(id, true);
    }

    @PostMapping("/{id}/resume")
    public MonitorResponse resume(@PathVariable long id) {
        return service.setPaused(id, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
