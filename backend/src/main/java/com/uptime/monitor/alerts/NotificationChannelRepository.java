package com.uptime.monitor.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {

    List<NotificationChannel> findByOwnerIdOrderByIdAsc(long ownerId);

    Optional<NotificationChannel> findByIdAndOwnerId(long id, long ownerId);

    long countByOwnerId(long ownerId);

    boolean existsByOwnerIdAndTypeAndTarget(long ownerId, ChannelType type, String target);
}
