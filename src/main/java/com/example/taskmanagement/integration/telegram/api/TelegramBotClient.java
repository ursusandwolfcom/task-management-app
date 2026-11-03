package com.example.taskmanagement.integration.telegram.api;

/** Outbound Bot API; tests substitute an in-memory recorder. */
public interface TelegramBotClient {
    void sendMessage(long chatId, String text);
}
