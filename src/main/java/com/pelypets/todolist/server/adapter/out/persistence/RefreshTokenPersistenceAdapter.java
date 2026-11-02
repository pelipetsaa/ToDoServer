package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.application.port.out.RefreshTokenRepository;
import com.pelypets.todolist.server.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository tokens;

    RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public void save(RefreshToken token) {
        tokens.save(RefreshTokenEntity.from(token));
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    @Override
    public boolean deleteByHash(String tokenHash) {
        return tokens.deleteByTokenHash(tokenHash) > 0;
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        tokens.deleteByUserId(userId);
    }

    @Override
    public void deleteExpiredForUser(UUID userId, Instant now) {
        tokens.deleteExpiredForUser(userId, now);
    }

    @Override
    public int deleteExpired(Instant now) {
        return tokens.deleteExpired(now);
    }
}
