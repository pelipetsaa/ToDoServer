package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.application.port.out.UserRepository;
import com.pelypets.todolist.server.domain.exception.EmailTakenException;
import com.pelypets.todolist.server.domain.exception.UnauthorizedException;
import com.pelypets.todolist.server.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class UserPersistenceAdapter implements UserRepository {

    private static final String COLUMNS = "id, email, password_hash, google_subject, created_at";

    private static final RowMapper<User> USER = (row, number) -> new User(
            row.getObject("id", UUID.class),
            row.getString("email"),
            row.getString("password_hash"),
            row.getString("google_subject"),
            Timestamps.read(row, "created_at"));

    private final JdbcClient jdbc;

    UserPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = :id").param("id", id).query(USER).optional();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE email = :email").param("email", email).query(USER).optional();
    }

    @Override
    public Optional<User> findByGoogleSubject(String subject) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE google_subject = :subject")
                .param("subject", subject)
                .query(USER)
                .optional();
    }

    /**
     * In PostgreSQL a failed INSERT aborts the whole transaction, so a unique violation could not be recovered from.
     * {@code ON CONFLICT DO NOTHING} reports the conflict as zero rows and keeps the transaction usable.
     */
    @Override
    public User create(User user) {
        int inserted = jdbc.sql("""
                        INSERT INTO users (id, email, password_hash, google_subject, created_at)
                        VALUES (:id, :email, :passwordHash, :googleSubject, :createdAt)
                        ON CONFLICT DO NOTHING""")
                .param("id", user.id())
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("googleSubject", user.googleSubject())
                .param("createdAt", Timestamps.of(user.createdAt()))
                .update();
        if (inserted == 0) throw new EmailTakenException();
        return user;
    }

    @Override
    public void linkGoogleSubject(UUID userId, String subject) {
        try {
            jdbc.sql("UPDATE users SET google_subject = :subject, password_hash = NULL WHERE id = :id")
                    .param("subject", subject)
                    .param("id", userId)
                    .update();
        } catch (DuplicateKeyException e) {
            // A concurrent sign-in created an account for this Google account in the meantime.
            throw new UnauthorizedException("This Google account is linked to another user");
        }
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", id).update();
    }
}
