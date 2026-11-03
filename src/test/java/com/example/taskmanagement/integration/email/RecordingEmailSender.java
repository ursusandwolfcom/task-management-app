package com.example.taskmanagement.integration.email;

import com.example.taskmanagement.integration.email.api.EmailSender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingEmailSender implements EmailSender {
    public record Sent(String to, String subject, String body, String htmlBody) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        sent.add(new Sent(to, subject, textBody, htmlBody));
    }

    public List<Sent> sent() {
        return new ArrayList<>(sent);
    }

    public void clear() {
        sent.clear();
    }
}
