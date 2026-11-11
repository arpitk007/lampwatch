package com.uptime.monitor.users;

import com.uptime.monitor.common.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String rawEmail, String password) {
        String email = AppUserDetailsService.normalize(rawEmail);
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists. Sign in instead.");
        }
        try {
            return users.saveAndFlush(new User(email, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the unique key decides.
            throw new ConflictException("An account with this email already exists. Sign in instead.");
        }
    }
}
