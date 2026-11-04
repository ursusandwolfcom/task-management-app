package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.MegaClient;
import com.example.taskmanagement.attachment.api.MegaException;
import com.example.taskmanagement.attachment.api.MegaOperations;
import com.example.taskmanagement.attachment.persistence.MegaLinkEntity;
import com.example.taskmanagement.attachment.persistence.MegaLinkRepository;
import com.example.taskmanagement.identity.api.IdentityOperations;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MegaAccountService implements MegaOperations {
    private final MegaLinkRepository links;
    private final MegaClient client;
    private final MegaSessionCipher cipher;
    private final IdentityOperations identity;
    private final Clock clock;

    public MegaAccountService(MegaLinkRepository links, MegaClient client, MegaSessionCipher cipher,
            IdentityOperations identity, Clock clock) {
        this.links = links;
        this.client = client;
        this.cipher = cipher;
        this.identity = identity;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Status status(long userId) {
        identity.currentRole(userId);
        return links.findById(userId).map(link -> new Status(configured(), true, link.accountId(), link.linkedAt()))
                .orElseGet(() -> new Status(configured(), false, null, null));
    }

    @Override
    public Status link(long userId, String email, String password, String mfaCode) {
        identity.currentRole(userId);
        if (!configured()) {
            throw new MegaException(MegaException.Reason.MEGA_NOT_CONFIGURED);
        }
        var session = client.login(email, password, mfaCode == null ? "" : mfaCode, "ProjectFlow-u" + userId);
        if (!validHandle(session.accountId()) || !validHandle(session.folderHandle())
                || session.session() == null || session.session().isBlank() || session.session().length() > 1024) {
            throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String encrypted = cipher.encrypt(userId, session.session());
        var link = links.findById(userId).orElseGet(() -> new MegaLinkEntity(userId, session.accountId(), encrypted,
                session.folderHandle(), now));
        link.update(session.accountId(), encrypted, session.folderHandle(), now);
        links.saveAndFlush(link);
        return new Status(true, true, session.accountId(), now);
    }

    @Override
    public Status unlink(long userId) {
        identity.currentRole(userId);
        links.findById(userId).ifPresent(links::delete);
        links.flush();
        return new Status(configured(), false, null, null);
    }

    @Transactional(readOnly = true)
    public Optional<MegaClient.Session> credential(long userId) {
        if (!configured()) {
            return Optional.empty();
        }
        return links.findById(userId).map(link -> new MegaClient.Session(link.accountId(),
                cipher.decrypt(userId, link.encryptedSession()), link.folderHandle()));
    }

    boolean configured() {
        return client.configured() && cipher.configured();
    }

    static boolean validHandle(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{8,16}");
    }
}
