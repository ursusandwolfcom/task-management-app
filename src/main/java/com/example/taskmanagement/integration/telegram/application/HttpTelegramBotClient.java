package com.example.taskmanagement.integration.telegram.application;

import com.example.taskmanagement.integration.telegram.api.TelegramBotClient;
import com.example.taskmanagement.integration.telegram.api.TelegramException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class HttpTelegramBotClient implements TelegramBotClient {
    private final RestClient http;

    public HttpTelegramBotClient(String botToken, RestClient.Builder builder) {
        this.http = builder.baseUrl("https://api.telegram.org/bot" + botToken).build();
    }

    @Override
    public void sendMessage(long chatId, String text) {
        try {
            http.post().uri("/sendMessage").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", text, "disable_web_page_preview", true))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new TelegramException(TelegramException.Reason.DELIVERY_FAILED);
        }
    }
}
