package com.uptime.monitor.monitors;

import com.uptime.monitor.common.BadRequestException;
import com.uptime.monitor.common.NotFoundException;
import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.incidents.Incident;
import com.uptime.monitor.incidents.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MonitorService {

    private final MonitorRepository monitors;
    private final IncidentRepository incidents;
    private final int maxPerUser;

    public MonitorService(MonitorRepository monitors, IncidentRepository incidents, UptimeProperties props) {
        this.monitors = monitors;
        this.incidents = incidents;
        this.maxPerUser = props.maxMonitorsPerUser();
    }

    @Transactional
    public MonitorResponse create(long ownerId, MonitorRequest req) {
        validate(req);
        if (monitors.countByOwnerId(ownerId) >= maxPerUser) {
            throw new BadRequestException("You can have up to " + maxPerUser + " monitors. Delete one to add another.");
        }
        Monitor monitor = new Monitor(ownerId, req.name().trim(), req.url().trim(), req.checkType(), keywordOf(req),
                req.intervalSeconds(), req.timeoutMs());
        return MonitorResponse.from(monitors.save(monitor));
    }

    @Transactional(readOnly = true)
    public List<MonitorResponse> list(long ownerId) {
        // Two queries for the whole list, not one per monitor.
        Map<Long, Instant> downSince = incidents.findOpenForOwner(ownerId).stream()
                .collect(Collectors.toMap(Incident::getMonitorId, Incident::getStartedAt));
        return monitors.findByOwnerIdOrderByNameAsc(ownerId).stream()
                .map(m -> MonitorResponse.from(m, downSince.get(m.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitorResponse get(long ownerId, long id) {
        Monitor monitor = find(ownerId, id);
        Instant downSince = incidents.findFirstByMonitorIdAndResolvedAtIsNull(id).map(Incident::getStartedAt).orElse(null);
        return MonitorResponse.from(monitor, downSince);
    }

    @Transactional
    public MonitorResponse update(long ownerId, long id, MonitorRequest req) {
        validate(req);
        Monitor monitor = find(ownerId, id);
        monitor.update(req.name().trim(), req.url().trim(), req.checkType(), keywordOf(req),
                req.intervalSeconds(), req.timeoutMs());
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse setPaused(long ownerId, long id, boolean paused) {
        Monitor monitor = find(ownerId, id);
        if (paused) {
            monitor.pause();
        } else {
            monitor.resume();
        }
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public void delete(long ownerId, long id) {
        monitors.delete(find(ownerId, id));
    }

    /** Another user's monitor looks exactly like a missing one, so ids can't be probed. */
    private Monitor find(long ownerId, long id) {
        return monitors.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new NotFoundException("Monitor " + id + " not found"));
    }

    private static void validate(MonitorRequest req) {
        if (req.checkType() == CheckType.KEYWORD && (req.keyword() == null || req.keyword().isBlank())) {
            throw new BadRequestException("keyword is required for KEYWORD checks");
        }
        if (req.timeoutMs() >= req.intervalSeconds() * 1000) {
            throw new BadRequestException("timeoutMs must be shorter than the check interval");
        }
    }

    private static String keywordOf(MonitorRequest req) {
        return req.checkType() == CheckType.KEYWORD ? req.keyword() : null;
    }
}
