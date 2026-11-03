package com.example.taskmanagement.identity.persistence;

import com.example.taskmanagement.identity.api.EmailDigestMode;
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
import java.time.Instant;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "email_digest_mode", nullable = false, length = 16)
    private EmailDigestMode emailDigestMode = EmailDigestMode.IMMEDIATE;
    @Column(name = "dropbox_refresh_token", length = 512)
    private String dropboxRefreshToken;
    @Column(name = "dropbox_account_id", length = 64)
    private String dropboxAccountId;
    @Column(name = "dropbox_linked_at")
    private Instant dropboxLinkedAt;

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

    public EmailDigestMode emailDigestMode() {
        return emailDigestMode;
    }

    public void setEmailDigestMode(EmailDigestMode mode) {
        this.emailDigestMode = mode == null ? EmailDigestMode.IMMEDIATE : mode;
    }

    public String dropboxRefreshToken() {
        return dropboxRefreshToken;
    }

    public String dropboxAccountId() {
        return dropboxAccountId;
    }

    public Instant dropboxLinkedAt() {
        return dropboxLinkedAt;
    }

    public void linkDropbox(String refreshToken, String accountId, Instant linkedAt) {
        this.dropboxRefreshToken = refreshToken;
        this.dropboxAccountId = accountId;
        this.dropboxLinkedAt = linkedAt;
    }

    public void unlinkDropbox() {
        this.dropboxRefreshToken = null;
        this.dropboxAccountId = null;
        this.dropboxLinkedAt = null;
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
