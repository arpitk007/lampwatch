package com.uptime.monitor.monitors;

import com.uptime.monitor.common.BadRequestException;
import com.uptime.monitor.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MonitorService {

    private final MonitorRepository monitors;

    public MonitorService(MonitorRepository monitors) {
        this.monitors = monitors;
    }

    @Transactional
    public MonitorResponse create(MonitorRequest req) {
        validate(req);
        Monitor monitor = new Monitor(req.name().trim(), req.url().trim(), req.checkType(), keywordOf(req),
                req.intervalSeconds(), req.timeoutMs());
        return MonitorResponse.from(monitors.save(monitor));
    }

    @Transactional(readOnly = true)
    public List<MonitorResponse> list() {
        return monitors.findAll().stream().map(MonitorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MonitorResponse get(long id) {
        return MonitorResponse.from(find(id));
    }

    @Transactional
    public MonitorResponse update(long id, MonitorRequest req) {
        validate(req);
        Monitor monitor = find(id);
        monitor.update(req.name().trim(), req.url().trim(), req.checkType(), keywordOf(req),
                req.intervalSeconds(), req.timeoutMs());
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse setPaused(long id, boolean paused) {
        Monitor monitor = find(id);
        if (paused) {
            monitor.pause();
        } else {
            monitor.resume();
        }
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public void delete(long id) {
        monitors.delete(find(id));
    }

    private Monitor find(long id) {
        return monitors.findById(id).orElseThrow(() -> new NotFoundException("Monitor " + id + " not found"));
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
