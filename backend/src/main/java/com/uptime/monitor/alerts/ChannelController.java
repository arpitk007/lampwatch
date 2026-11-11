package com.uptime.monitor.alerts;

import com.uptime.monitor.users.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService service;

    public ChannelController(ChannelService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChannelResponse> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelResponse add(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChannelRequest req) {
        return service.add(user.id(), req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        service.delete(user.id(), id);
    }

    /** Queues a test alert; it arrives within a few seconds. */
    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void test(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        service.sendTest(user.id(), id);
    }
}
