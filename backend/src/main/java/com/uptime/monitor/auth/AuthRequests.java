package com.uptime.monitor.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    /** 72 is bcrypt's input limit; longer passwords would be silently truncated. */
    public record Signup(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72, message = "must be 10 to 72 characters") String password) {

        /** Runs before validation, so pasted spaces around the email don't fail @Email. */
        public Signup {
            email = email == null ? null : email.trim();
        }
    }

    public record Login(@NotBlank String email, @NotBlank String password) {
    }

    public record UserResponse(long id, String email) {
    }
}
