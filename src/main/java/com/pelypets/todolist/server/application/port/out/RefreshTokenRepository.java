package com.pelypets.todolist.server.application.port.out;

import com.pelypets.todolist.server.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void save(RefreshToken token);

    Optional<RefreshToken> findByHash(String tokenHash);

    /** False when nothing was deleted, e.g. a concurrent refresh used the token first. */
    boolean deleteByHash(String tokenHash);

    void deleteAllForUser(UUID userId);

    void deleteExpiredForUser(UUID userId, Instant now);

    int deleteExpired(Instant now);
}
