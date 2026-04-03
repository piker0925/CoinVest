package com.coinvest.portfolio.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Discord Webhook 연동 클라이언트.
 */
@Slf4j
@Component
@AllArgsConstructor
public class DiscordClient {

    private final RestTemplate restTemplate;

    /**
     * Discord Webhook 메시지 전송.
     */
    public void send(String webhookUrl, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("content", message);

        try {
            restTemplate.postForEntity(webhookUrl, body, String.class);
        } catch (Exception e) {
            log.error("Failed to send discord webhook: {}", e.getMessage());
            throw e;
        }
    }
}
