package com.uptime.monitor.alerts;

import java.time.Instant;

/** A channel plus how its most recent alert went, so delivery problems are visible. */
public record ChannelResponse(long id, ChannelType type, String target, LastDelivery lastDelivery) {

    public record LastDelivery(String subject, String status, Instant at, String error) {
    }
}
