package com.uptime.monitor.incidents;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByMonitorIdAndResolvedAtIsNull(long monitorId);

    List<Incident> findByMonitorIdOrderByStartedAtDesc(long monitorId, Pageable page);

    /** Open incidents across one user's monitors, for the dashboard's "down since". */
    @Query("""
            select i from Incident i, com.uptime.monitor.monitors.Monitor m
            where i.monitorId = m.id and m.ownerId = :owner and i.resolvedAt is null""")
    List<Incident> findOpenForOwner(@Param("owner") long ownerId);
}
