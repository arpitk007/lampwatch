package com.uptime.monitor.alerts;

import com.uptime.monitor.config.UptimeProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Sends through the Telegram Bot API. The bot token comes from configuration, never from users. */
@Component
public class TelegramAlertSender implements AlertSender {

    private final RestClient http;
    private final String token;

    public TelegramAlertSender(UptimeProperties props) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(5_000);
        timeouts.setReadTimeout(10_000);
        this.http = RestClient.builder().baseUrl(props.alerts().telegramApiBase()).requestFactory(timeouts).build();
        this.token = props.alerts().telegramBotToken();
    }

    @Override
    public ChannelType type() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public void send(String chatId, String subject, String body) {
        if (token.isBlank()) {
            throw new IllegalStateException("Telegram is not configured on this server");
        }
        http.post()
                .uri("/bot{token}/sendMessage", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", subject + "\n\n" + body, "disable_web_page_preview", true))
                .retrieve()
                .toBodilessEntity(); // a 4xx/5xx throws, so the outbox retries
    }
}
