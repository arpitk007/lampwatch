package com.uptime.monitor.statuspages;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The owner's view of their status page; also the request body for saving it. */
public record StatusPageSettings(
        @NotBlank @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$",
                message = "use 3 to 40 lowercase letters, digits or hyphens, not starting or ending with a hyphen") String slug,
        @NotBlank @Size(max = 80) String title,
        @NotNull @Size(max = 20) List<Long> monitorIds) {

    public StatusPageSettings {
        slug = slug == null ? null : slug.trim().toLowerCase();
        title = title == null ? null : title.trim();
    }
}
