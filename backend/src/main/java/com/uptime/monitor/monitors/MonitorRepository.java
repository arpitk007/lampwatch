package com.uptime.monitor.monitors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    @Modifying
    @Query("""
            update Monitor m
            set m.status = com.uptime.monitor.monitors.MonitorStatus.UP,
                m.consecutiveFailures = 0,
                m.lastCheckedAt = :at
            where m.id = :id""")
    int recordSuccess(@Param("id") long id, @Param("at") Instant at);

    /** Status is assigned first so it reads the failure count from before this check. */
    @Modifying
    @Query("""
            update Monitor m
            set m.status = case when m.consecutiveFailures + 1 >= :threshold
                                then com.uptime.monitor.monitors.MonitorStatus.DOWN
                                else m.status end,
                m.consecutiveFailures = m.consecutiveFailures + 1,
                m.lastCheckedAt = :at
            where m.id = :id""")
    int recordFailure(@Param("id") long id, @Param("at") Instant at, @Param("threshold") int threshold);
}
