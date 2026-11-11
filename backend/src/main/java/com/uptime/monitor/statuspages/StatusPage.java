package com.uptime.monitor.statuspages;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "status_pages")
public class StatusPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private long ownerId;

    @Column(nullable = false, length = 40)
    private String slug;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StatusPage() {
    }

    public StatusPage(long ownerId, String slug, String title) {
        this.ownerId = ownerId;
        this.slug = slug;
        this.title = title;
        this.createdAt = this.updatedAt = Instant.now();
    }

    public void update(String slug, String title) {
        this.slug = slug;
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
}
