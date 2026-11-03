package com.example.taskmanagement.integration.email.api;

public interface EmailSender {
    /** Sends multipart alternative: plain text + HTML. */
    void send(String to, String subject, String textBody, String htmlBody);
}
