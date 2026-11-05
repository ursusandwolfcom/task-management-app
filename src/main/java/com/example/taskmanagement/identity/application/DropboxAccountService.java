package com.example.taskmanagement.identity.application;

import com.example.taskmanagement.identity.api.DropboxAccountOperations;
import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.persistence.UserRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class DropboxAccountService implements DropboxAccountOperations {
    private final UserRepository users;
    private final Clock clock;
    private final DropboxCredentialCipher cipher;

    public DropboxAccountService(UserRepository users, Clock clock, DropboxCredentialCipher cipher) {
        this.users = users;
        this.clock = clock;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public DropboxStatus status(long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        return new DropboxStatus(user.encryptedDropboxRefreshToken() != null,
                user.dropboxAccountId(), user.dropboxLinkedAt());
    }

    @Override
    public boolean credentialStorageConfigured() {
        return cipher.configured();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DropboxCredential> credential(long userId) {
        return users.findById(userId)
                .filter(user -> user.encryptedDropboxRefreshToken() != null)
                .map(user -> new DropboxCredential(cipher.decrypt(userId, user.encryptedDropboxRefreshToken()),
                        user.dropboxAccountId()));
    }

    @Override
    public void link(long userId, String refreshToken, String accountId) {
        if (refreshToken == null || refreshToken.isBlank() || refreshToken.strip().length() > 512
                || accountId == null || accountId.isBlank() || accountId.strip().length() > 64) {
            throw new IdentityException(IdentityException.Reason.VALIDATION_ERROR);
        }
        var user = users.findById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        try {
            user.linkDropbox(cipher.encrypt(userId, refreshToken.strip()), accountId.strip(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new IdentityException(IdentityException.Reason.DROPBOX_ACCOUNT_TAKEN);
        }
    }

    @Override
    public void unlink(long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        user.unlinkDropbox();
        users.saveAndFlush(user);
    }
}
