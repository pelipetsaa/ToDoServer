package com.pelypets.todolist.server.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored refresh token. Only its SHA-256 hash is kept, so a database leak does not leak sessions. Every token of one
 * sign-in shares a {@code familyId}: refreshing replaces the token but keeps the family.
 */
public record RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
