package com.uptime.monitor.alerts;

import com.uptime.monitor.config.UptimeProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailAlertSender implements AlertSender {

    private final JavaMailSender mail;
    private final String from;

    public EmailAlertSender(JavaMailSender mail, UptimeProperties props) {
        this.mail = mail;
        this.from = props.alerts().mailFrom();
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(String target, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(target);
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
    }
}
