package com.example.taskmanagement.integration.telegram.application;

import com.example.taskmanagement.integration.telegram.api.TelegramBotClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class TelegramClientConfiguration {
    @Bean
    TelegramBotClient telegramBotClient(@Value("${telegram.bot-token:}") String botToken) {
        String token = botToken == null ? "" : botToken.strip();
        if (token.isEmpty()) {
            return new NoOpTelegramBotClient();
        }
        return new HttpTelegramBotClient(token, RestClient.builder());
    }
}
