package com.uptime.monitor.statuspages;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds a public status page from hourly rollups: four queries regardless of history length. */
@Component
public class PublicStatusQuery {

    static final int DAYS = 30;

    private final JdbcClient jdbc;
    private final StatusPageRepository pages;

    public PublicStatusQuery(JdbcClient jdbc, StatusPageRepository pages) {
        this.jdbc = jdbc;
        this.pages = pages;
    }

    private record PageMonitor(long id, String name, String status, boolean paused) {
    }

    private record Totals(long checks, long failures) {
        /** Percentage to 2 decimals. Any failure caps it at 99.99, so real downtime never displays as 100%. */
        Double uptime() {
            if (checks == 0) return null;
            double pct = Math.round((checks - failures) * 10000.0 / checks) / 100.0;
            return failures > 0 ? Math.min(pct, 99.99) : pct;
        }
    }

    @Transactional(readOnly = true)
    public Optional<PublicStatusPage> build(String slug) {
        return pages.findBySlug(slug).map(this::build);
    }

    private PublicStatusPage build(StatusPage page) {
        List<PageMonitor> monitors = jdbc.sql("""
                        SELECT m.id, m.name, m.status, m.paused
                        FROM status_page_monitors spm JOIN monitors m ON m.id = spm.monitor_id
                        WHERE spm.page_id = :page
                        ORDER BY spm.position""")
                .param("page", page.getId())
                .query((rs, n) -> new PageMonitor(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)))
                .list();
        List<Long> ids = monitors.stream().map(PageMonitor::id).toList();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstDay = today.minusDays(DAYS - 1);
        Map<Long, Map<LocalDate, Totals>> daily = ids.isEmpty() ? Map.of() : dailyTotals(ids, firstDay);
        Map<Long, Totals> last24h = ids.isEmpty() ? Map.of() : totalsSince(ids, Instant.now().minus(24, ChronoUnit.HOURS));

        List<PublicStatusPage.Monitor> out = new ArrayList<>();
        for (PageMonitor m : monitors) {
            Map<LocalDate, Totals> byDay = daily.getOrDefault(m.id(), Map.of());
            List<PublicStatusPage.Day> days = new ArrayList<>();
            long checks = 0, failures = 0;
            for (LocalDate d = firstDay; !d.isAfter(today); d = d.plusDays(1)) {
                Totals t = byDay.get(d);
                days.add(new PublicStatusPage.Day(d, t == null ? null : t.uptime()));
                if (t != null) {
                    checks += t.checks();
                    failures += t.failures();
                }
            }
            out.add(new PublicStatusPage.Monitor(m.name(), m.paused() ? "PAUSED" : m.status(),
                    last24h.getOrDefault(m.id(), new Totals(0, 0)).uptime(), new Totals(checks, failures).uptime(), days));
        }

        return new PublicStatusPage(page.getTitle(), overall(monitors), Instant.now(), out,
                ids.isEmpty() ? List.of() : recentIncidents(ids));
    }

    private Map<Long, Map<LocalDate, Totals>> dailyTotals(List<Long> ids, LocalDate firstDay) {
        Map<Long, Map<LocalDate, Totals>> result = new HashMap<>();
        jdbc.sql("""
                        SELECT monitor_id, DATE(hour_start) AS day, SUM(checks), SUM(failures)
                        FROM check_rollups_hourly
                        WHERE monitor_id IN (:ids) AND hour_start >= :since
                        GROUP BY monitor_id, DATE(hour_start)""")
                .param("ids", ids)
                .param("since", firstDay.atStartOfDay())
                .query(rs -> {
                    result.computeIfAbsent(rs.getLong(1), k -> new HashMap<>())
                            .put(rs.getObject(2, LocalDate.class), new Totals(rs.getLong(3), rs.getLong(4)));
                });
        return result;
    }

    private Map<Long, Totals> totalsSince(List<Long> ids, Instant since) {
        Map<Long, Totals> result = new HashMap<>();
        jdbc.sql("""
                        SELECT monitor_id, SUM(checks), SUM(failures)
                        FROM check_rollups_hourly
                        WHERE monitor_id IN (:ids) AND hour_start >= :since
                        GROUP BY monitor_id""")
                .param("ids", ids)
                .param("since", LocalDateTime.ofInstant(since.truncatedTo(ChronoUnit.HOURS), ZoneOffset.UTC))
                .query(rs -> {
                    result.put(rs.getLong(1), new Totals(rs.getLong(2), rs.getLong(3)));
                });
        return result;
    }

    private List<PublicStatusPage.Incident> recentIncidents(List<Long> ids) {
        return jdbc.sql("""
                        SELECT m.name, i.started_at, i.resolved_at
                        FROM incidents i JOIN monitors m ON m.id = i.monitor_id
                        WHERE i.monitor_id IN (:ids) AND i.started_at >= :since
                        ORDER BY i.started_at DESC
                        LIMIT 20""")
                .param("ids", ids)
                .param("since", LocalDateTime.now(ZoneOffset.UTC).minusDays(14))
                .query((rs, n) -> new PublicStatusPage.Incident(rs.getString(1),
                        rs.getObject(2, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getObject(3, LocalDateTime.class) == null ? null : rs.getObject(3, LocalDateTime.class).toInstant(ZoneOffset.UTC)))
                .list();
    }

    static PublicStatusPage.Overall overall(List<PageMonitor> monitors) {
        List<PageMonitor> active = monitors.stream().filter(m -> !m.paused()).toList();
        long down = active.stream().filter(m -> m.status().equals("DOWN")).count();
        if (down > 0 && down == active.size()) return PublicStatusPage.Overall.MAJOR_OUTAGE;
        if (down > 0) return PublicStatusPage.Overall.PARTIAL_OUTAGE;
        if (active.stream().anyMatch(m -> m.status().equals("DEGRADED"))) return PublicStatusPage.Overall.DEGRADED;
        return PublicStatusPage.Overall.OPERATIONAL;
    }
}
