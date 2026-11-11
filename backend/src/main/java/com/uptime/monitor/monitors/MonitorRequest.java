package com.uptime.monitor.monitors;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MonitorRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "^https?://\\S+$", message = "must be an http or https URL") String url,
        @NotNull CheckType checkType,
        @Size(max = 255) String keyword,
        @Min(60) @Max(3600) int intervalSeconds,
        @Min(1000) @Max(30000) int timeoutMs) {
}
