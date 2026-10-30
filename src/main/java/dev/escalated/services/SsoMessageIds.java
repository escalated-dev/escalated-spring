package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.SsoMessageId;
import dev.escalated.repositories.SsoMessageIdRepository;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SSO message IDs kept server side (ADR 2026-10-29-configurable-access-features): issued SAML
 * AuthnRequest IDs (not in the session: the IdP's cross-site POST carries no SameSite=Lax cookie),
 * and accepted assertion IDs and JWT {@code jti}s, so none is accepted twice.
 */
@Service
public class SsoMessageIds {

    private final SsoMessageIdRepository ids;

    public SsoMessageIds(SsoMessageIdRepository ids) {
        this.ids = ids;
    }

    /** Records an ID this install issued, answerable until {@code expiresAt}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void remember(String kind, String id, long expiresAt) {
        ids.purgeExpired(Instant.now().getEpochSecond());
        ids.save(row(kind, id, expiresAt));
    }

    /** Uses up an issued ID: true once, while it is unexpired. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public boolean consume(String kind, String id) {
        return id != null && ids.consume(kind, id, Instant.now().getEpochSecond()) == 1;
    }

    /** Claims an ID seen for the first time: false when it was already claimed (a replay). */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public boolean claim(String kind, String id, long expiresAt) {
        if (id == null || id.isBlank() || id.length() > 191) {
            return false;
        }
        ids.purgeExpired(Instant.now().getEpochSecond());
        if (ids.existsByKindAndMessageId(kind, id)) {
            return false;
        }
        try {
            ids.saveAndFlush(row(kind, id, expiresAt));
            return true;
        } catch (DataIntegrityViolationException raced) {
            return false;
        }
    }

    private static SsoMessageId row(String kind, String id, long expiresAt) {
        SsoMessageId row = new SsoMessageId();
        row.setKind(kind);
        row.setMessageId(id);
        row.setExpiresAt(expiresAt);
        return row;
    }
}
