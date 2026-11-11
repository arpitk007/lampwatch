package com.uptime.monitor.alerts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChannelRequest(@NotNull ChannelType type, @NotBlank @Size(max = 254) String target) {

    public ChannelRequest {
        target = target == null ? null : target.trim();
    }
}
