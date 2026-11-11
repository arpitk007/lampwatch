package com.uptime.monitor.monitors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Dynamic updates write only changed columns, so a user edit never overwrites
 * the status and counters that checks update in parallel. Those fields are also
 * excluded from optimistic locking, so a check landing mid-edit doesn't fail the edit.
 */
@Entity
@DynamicUpdate
@Table(name = "monitors")
public class Monitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private long ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "check_type", nullable = false, length = 20)
    private CheckType checkType;

    private String keyword;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @OptimisticLock(excluded = true)
    private MonitorStatus status = MonitorStatus.UNKNOWN;

    @Column(name = "consecutive_failures", nullable = false)
    @OptimisticLock(excluded = true)
    private int consecutiveFailures;

    @Column(name = "consecutive_successes", nullable = false)
    @OptimisticLock(excluded = true)
    private int consecutiveSuccesses;

    @Column(nullable = false)
    private boolean paused;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "last_checked_at")
    @OptimisticLock(excluded = true)
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Monitor() {
    }

    public Monitor(long ownerId, String name, String url, CheckType checkType, String keyword, int intervalSeconds, int timeoutMs) {
        this.ownerId = ownerId;
        this.name = name;
        this.url = url;
        this.checkType = checkType;
        this.keyword = keyword;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMs = timeoutMs;
        this.nextCheckAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    /** updated_at means "settings last edited", so it is set here and not on status writes from checks. */
    public void update(String name, String url, CheckType checkType, String keyword, int intervalSeconds, int timeoutMs) {
        this.updatedAt = Instant.now();
        this.name = name;
        this.url = url;
        this.checkType = checkType;
        this.keyword = keyword;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMs = timeoutMs;
    }

    /** Counts this check in the run of passes or failures. Returns the new run lengths. */
    public void countCheck(boolean success, Instant at) {
        if (success) {
            consecutiveSuccesses++;
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
            consecutiveSuccesses = 0;
        }
        lastCheckedAt = at;
    }

    public void setStatus(MonitorStatus status) {
        this.status = status;
    }

    public void pause() {
        paused = true;
    }

    /** Resuming checks immediately so the status is fresh again. */
    public void resume() {
        paused = false;
        nextCheckAt = Instant.now();
    }

    public Long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public CheckType getCheckType() { return checkType; }
    public String getKeyword() { return keyword; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public int getTimeoutMs() { return timeoutMs; }
    public MonitorStatus getStatus() { return status; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public boolean isPaused() { return paused; }
    public Instant getNextCheckAt() { return nextCheckAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
