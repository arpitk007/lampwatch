package com.uptime.monitor.statuspages;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatusPageRepository extends JpaRepository<StatusPage, Long> {

    Optional<StatusPage> findByOwnerId(long ownerId);

    Optional<StatusPage> findBySlug(String slug);
}
