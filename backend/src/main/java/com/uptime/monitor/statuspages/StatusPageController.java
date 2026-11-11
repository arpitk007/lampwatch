package com.uptime.monitor.statuspages;

import com.uptime.monitor.common.NotFoundException;
import com.uptime.monitor.users.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class StatusPageController {

    private final StatusPageService service;
    private final StatusPageCache cache;

    public StatusPageController(StatusPageService service, StatusPageCache cache) {
        this.service = service;
        this.cache = cache;
    }

    /** The signed-in user's page settings; 404 until they create one. */
    @GetMapping("/api/status-page")
    public StatusPageSettings mine(@AuthenticationPrincipal AuthUser user) {
        return service.get(user.id()).orElseThrow(() -> new NotFoundException("You don't have a status page yet."));
    }

    @PutMapping("/api/status-page")
    public StatusPageSettings save(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody StatusPageSettings req) {
        return service.save(user.id(), req);
    }

    @DeleteMapping("/api/status-page")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user) {
        service.delete(user.id());
    }

    /** Public, no sign-in. Browsers and CDNs may also cache it briefly. */
    @GetMapping("/api/public/status-pages/{slug}")
    public ResponseEntity<PublicStatusPage> publicPage(@PathVariable String slug) {
        return cache.get(slug.toLowerCase())
                .map(page -> ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()).body(page))
                .orElseThrow(() -> new NotFoundException("No status page at this address."));
    }
}
