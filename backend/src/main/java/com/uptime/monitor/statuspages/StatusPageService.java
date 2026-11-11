package com.uptime.monitor.statuspages;

import com.uptime.monitor.common.BadRequestException;
import com.uptime.monitor.common.ConflictException;
import com.uptime.monitor.common.NotFoundException;
import com.uptime.monitor.monitors.MonitorRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class StatusPageService {

    private final StatusPageRepository pages;
    private final MonitorRepository monitors;
    private final JdbcClient jdbc;
    private final StatusPageCache cache;

    public StatusPageService(StatusPageRepository pages, MonitorRepository monitors, JdbcClient jdbc, StatusPageCache cache) {
        this.pages = pages;
        this.monitors = monitors;
        this.jdbc = jdbc;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public Optional<StatusPageSettings> get(long ownerId) {
        return pages.findByOwnerId(ownerId).map(p -> new StatusPageSettings(p.getSlug(), p.getTitle(), monitorIds(p.getId())));
    }

    /** Creates or replaces the owner's page. */
    @Transactional
    public StatusPageSettings save(long ownerId, StatusPageSettings req) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(req.monitorIds())); // keep order, drop repeats
        for (Long id : ids) {
            if (!monitors.existsByIdAndOwnerId(id, ownerId)) {
                throw new BadRequestException("Monitor " + id + " isn't one of yours.");
            }
        }
        pages.findBySlug(req.slug())
                .filter(other -> other.getOwnerId() != ownerId)
                .ifPresent(other -> {
                    throw new ConflictException("That address is taken. Try another.");
                });

        StatusPage page = pages.findByOwnerId(ownerId).orElse(null);
        String oldSlug = page == null ? null : page.getSlug();
        if (page == null) {
            page = pages.save(new StatusPage(ownerId, req.slug(), req.title()));
        } else {
            page.update(req.slug(), req.title());
        }

        jdbc.sql("DELETE FROM status_page_monitors WHERE page_id = :page").param("page", page.getId()).update();
        for (int i = 0; i < ids.size(); i++) {
            jdbc.sql("INSERT INTO status_page_monitors (page_id, monitor_id, position) VALUES (:page, :monitor, :pos)")
                    .param("page", page.getId()).param("monitor", ids.get(i)).param("pos", i)
                    .update();
        }

        if (oldSlug != null) cache.evict(oldSlug);
        cache.evict(req.slug());
        return new StatusPageSettings(page.getSlug(), page.getTitle(), ids);
    }

    @Transactional
    public void delete(long ownerId) {
        StatusPage page = pages.findByOwnerId(ownerId).orElseThrow(() -> new NotFoundException("You don't have a status page."));
        pages.delete(page);
        cache.evict(page.getSlug());
    }

    private List<Long> monitorIds(long pageId) {
        return jdbc.sql("SELECT monitor_id FROM status_page_monitors WHERE page_id = :page ORDER BY position")
                .param("page", pageId).query(Long.class).list();
    }
}
