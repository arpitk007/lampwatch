package com.uptime.monitor.alerts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "notification_channels")
public class NotificationChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private long ownerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ChannelType type;

    @Column(nullable = false, length = 254)
    private String target;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationChannel() {
    }

    public NotificationChannel(long ownerId, ChannelType type, String target) {
        this.ownerId = ownerId;
        this.type = type;
        this.target = target;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public ChannelType getType() { return type; }
    public String getTarget() { return target; }
    public Instant getCreatedAt() { return createdAt; }
}
