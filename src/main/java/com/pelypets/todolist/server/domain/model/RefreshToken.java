package com.pelypets.todolist.server.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A stored refresh token. Only its SHA-256 hash is kept, so a database leak does not leak sessions. */
public record RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt) {

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
