package com.uptime.monitor.alerts;

/** One implementation per channel type (strategy pattern). Throws when delivery fails, so it is retried. */
public interface AlertSender {

    ChannelType type();

    void send(String target, String subject, String body) throws Exception;
}
