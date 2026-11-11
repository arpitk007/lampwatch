package com.uptime.monitor.users;

import com.uptime.monitor.alerts.ChannelType;
import com.uptime.monitor.alerts.NotificationChannel;
import com.uptime.monitor.alerts.NotificationChannelRepository;
import com.uptime.monitor.common.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final NotificationChannelRepository channels;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, NotificationChannelRepository channels, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.channels = channels;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String rawEmail, String password) {
        String email = AppUserDetailsService.normalize(rawEmail);
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists. Sign in instead.");
        }
        try {
            User user = users.saveAndFlush(new User(email, passwordEncoder.encode(password)));
            channels.save(new NotificationChannel(user.getId(), ChannelType.EMAIL, email)); // alerts on by default
            return user;
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the unique key decides.
            throw new ConflictException("An account with this email already exists. Sign in instead.");
        }
    }
}
