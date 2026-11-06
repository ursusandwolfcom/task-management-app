package com.example.taskmanagement.service;

public record TaskEmail(String recipient, String subject, String text) {
    @Override
    public String toString() {
        return "TaskEmail[content redacted]";
    }
}
