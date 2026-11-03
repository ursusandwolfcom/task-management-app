package com.example.taskmanagement.integration.email.api;

public interface EmailSender {
    void send(String to, String subject, String body);
}
