package com.example.taskmanagement.integration.telegram;

import com.example.taskmanagement.integration.telegram.api.TelegramBotClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingTelegramBotClient implements TelegramBotClient {
    public record Sent(long chatId, String text) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendMessage(long chatId, String text) {
        sent.add(new Sent(chatId, text));
    }

    public List<Sent> sent() {
        return new ArrayList<>(sent);
    }

    public void clear() {
        sent.clear();
    }
}
