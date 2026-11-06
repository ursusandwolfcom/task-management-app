package com.example.taskmanagement.dto;

public record TokenResponse(
        String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() {
        return "TokenResponse[credentials redacted]";
    }
}
