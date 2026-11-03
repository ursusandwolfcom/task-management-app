package com.example.taskmanagement.identity.persistence;

import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.identity.api.UserView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "users", schema = "task_management")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String username;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role;
    @Version
    private long version;
    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;
    @Column(name = "telegram_chat_id")
    private Long telegramChatId;
    @Column(name = "telegram_enabled", nullable = false)
    private boolean telegramEnabled = true;
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    protected UserEntity() {
    }

    public UserEntity(String username, String email, String hash, String firstName, String lastName, UserRole role) {
        this.username = username;
        this.email = email;
        this.passwordHash = hash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
    }

    public UserView view() {
        return new UserView(id, username, email, firstName, lastName, role, version);
    }

    public String passwordHash() {
        return passwordHash;
    }

    public long credentialVersion() {
        return credentialVersion;
    }

    public Long telegramChatId() {
        return telegramChatId;
    }

    public boolean telegramEnabled() {
        return telegramEnabled;
    }

    public void bindTelegramChat(long chatId) {
        this.telegramChatId = chatId;
        this.telegramEnabled = true;
    }

    public void unlinkTelegram() {
        this.telegramChatId = null;
    }

    public void setTelegramEnabled(boolean enabled) {
        this.telegramEnabled = enabled;
    }

    public String email() {
        return email;
    }

    public boolean emailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean enabled) {
        this.emailEnabled = enabled;
    }

    public void update(String username, String email, String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void changePassword(String hash) {
        passwordHash = hash;
        credentialVersion++;
    }

    public void changeRole(UserRole newRole) {
        role = newRole;
    }
}
