package com.example.taskmanagement.identity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramLinkTokenRepository extends JpaRepository<TelegramLinkTokenEntity, String> {
}
