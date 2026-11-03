package com.example.taskmanagement.integration.telegram.application;

import com.example.taskmanagement.integration.telegram.api.TelegramBotClient;
import com.example.taskmanagement.integration.telegram.api.TelegramException;

public class NoOpTelegramBotClient implements TelegramBotClient {
    @Override
    public void sendMessage(long chatId, String text) {
        throw new TelegramException(TelegramException.Reason.DISABLED);
    }
}
