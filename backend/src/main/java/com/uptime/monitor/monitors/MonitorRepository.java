package com.uptime.monitor.monitors;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    List<Monitor> findByOwnerIdOrderByNameAsc(long ownerId);

    Optional<Monitor> findByIdAndOwnerId(long id, long ownerId);

    boolean existsByIdAndOwnerId(long id, long ownerId);

    long countByOwnerId(long ownerId);

    /** Row lock while a check result is applied, so two results for one monitor apply in order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Monitor m where m.id = :id")
    Optional<Monitor> findByIdForUpdate(@Param("id") long id);
}
