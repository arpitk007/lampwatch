package com.uptime.monitor.alerts;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Picks the sender for a channel type. Fails at startup if any type has no sender. */
@Component
public class AlertSenderRegistry {

    private final Map<ChannelType, AlertSender> senders = new EnumMap<>(ChannelType.class);

    public AlertSenderRegistry(List<AlertSender> all) {
        for (AlertSender sender : all) {
            if (senders.put(sender.type(), sender) != null) {
                throw new IllegalStateException("Two senders registered for " + sender.type());
            }
        }
        for (ChannelType type : ChannelType.values()) {
            if (!senders.containsKey(type)) {
                throw new IllegalStateException("No sender registered for " + type);
            }
        }
    }

    public AlertSender forType(ChannelType type) {
        return senders.get(type);
    }
}
