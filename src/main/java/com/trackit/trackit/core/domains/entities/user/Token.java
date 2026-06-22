package com.trackit.trackit.core.domains.entities.user;

import java.time.Instant;
import java.util.UUID;

public class Token {
    public final String id;
    public final String tokenHash;
    public final String targetId;
    public final String purpose;
    public final Instant expiresAt;
    public final Instant createdAt;
    public final boolean isNew;

    public Token(String tokenHash, String targetId, String purpose, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.tokenHash = tokenHash;
        this.targetId = targetId;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.isNew = true;
    }

    private Token(String id, String tokenHash, String targetId, String purpose, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.targetId = targetId;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.isNew = false;
    }

    public static Token reconstruct(String id, String tokenHash, String targetId, String purpose, Instant expiresAt, Instant createdAt) {
        return new Token(id, tokenHash, targetId, purpose, expiresAt, createdAt);
    }
}
