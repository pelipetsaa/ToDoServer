package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.application.port.out.RefreshTokenRepository;
import com.pelypets.todolist.server.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    /** Key of the PostgreSQL advisory lock that lets one instance at a time purge expired tokens. */
    private static final long PURGE_LOCK = 0x7265667265736850L;

    private static final RowMapper<RefreshToken> TOKEN = (row, number) -> new RefreshToken(
            row.getObject("id", UUID.class),
            row.getObject("user_id", UUID.class),
            row.getObject("family_id", UUID.class),
            row.getString("token_hash"),
            Timestamps.read(row, "created_at"),
            Timestamps.read(row, "expires_at"));

    private final JdbcClient jdbc;

    RefreshTokenPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(RefreshToken token) {
        jdbc.sql("""
                        INSERT INTO refresh_tokens (id, user_id, family_id, token_hash, created_at, expires_at)
                        VALUES (:id, :userId, :familyId, :tokenHash, :createdAt, :expiresAt)""")
                .param("id", token.id())
                .param("userId", token.userId())
                .param("familyId", token.familyId())
                .param("tokenHash", token.tokenHash())
                .param("createdAt", Timestamps.of(token.createdAt()))
                .param("expiresAt", Timestamps.of(token.expiresAt()))
                .update();
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return jdbc.sql("""
                        SELECT id, user_id, family_id, token_hash, created_at, expires_at
                        FROM refresh_tokens WHERE token_hash = :tokenHash""")
                .param("tokenHash", tokenHash)
                .query(TOKEN)
                .optional();
    }

    /** One statement, so the token cannot be deleted without being remembered. */
    @Override
    public boolean consume(String tokenHash) {
        return jdbc.sql("""
                        WITH consumed AS (
                            DELETE FROM refresh_tokens WHERE token_hash = :tokenHash
                            RETURNING token_hash, user_id, family_id, expires_at)
                        INSERT INTO used_refresh_tokens (token_hash, user_id, family_id, expires_at)
                        SELECT token_hash, user_id, family_id, expires_at FROM consumed
                        ON CONFLICT DO NOTHING""")
                .param("tokenHash", tokenHash)
                .update() > 0;
    }

    @Override
    public Optional<UUID> findUsedFamily(String tokenHash) {
        return jdbc.sql("SELECT family_id FROM used_refresh_tokens WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash)
                .query(UUID.class)
                .optional();
    }

    @Override
    public void deleteFamily(UUID familyId) {
        jdbc.sql("DELETE FROM refresh_tokens WHERE family_id = :familyId").param("familyId", familyId).update();
    }

    @Override
    public boolean deleteByHash(String tokenHash) {
        return jdbc.sql("DELETE FROM refresh_tokens WHERE token_hash = :tokenHash").param("tokenHash", tokenHash).update() > 0;
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        jdbc.sql("DELETE FROM refresh_tokens WHERE user_id = :userId").param("userId", userId).update();
    }

    @Override
    public void deleteExpiredForUser(UUID userId, Instant now) {
        jdbc.sql("DELETE FROM refresh_tokens WHERE user_id = :userId AND expires_at <= :now")
                .param("userId", userId)
                .param("now", Timestamps.of(now))
                .update();
    }

    @Override
    public void keepNewestForUser(UUID userId, int count) {
        jdbc.sql("""
                        DELETE FROM refresh_tokens
                        WHERE user_id = :userId AND id NOT IN (
                            SELECT id FROM refresh_tokens WHERE user_id = :userId
                            ORDER BY created_at DESC, id LIMIT :count)""")
                .param("userId", userId)
                .param("count", count)
                .update();
    }

    @Override
    public int deleteExpired(Instant now) {
        boolean locked = jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)").param("key", PURGE_LOCK).query(Boolean.class).single();
        if (!locked) return 0;
        int tokens = jdbc.sql("DELETE FROM refresh_tokens WHERE expires_at <= :now").param("now", Timestamps.of(now)).update();
        int used = jdbc.sql("DELETE FROM used_refresh_tokens WHERE expires_at <= :now").param("now", Timestamps.of(now)).update();
        return tokens + used;
    }
}
