package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.application.port.out.UserRepository;
import com.pelypets.todolist.server.domain.exception.EmailTakenException;
import com.pelypets.todolist.server.domain.model.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository users;
    private final JdbcClient jdbc;

    UserPersistenceAdapter(UserJpaRepository users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return users.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.findByEmail(email).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByGoogleSubject(String subject) {
        return users.findByGoogleSubject(subject).map(UserEntity::toDomain);
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
                .param("createdAt", OffsetDateTime.ofInstant(user.createdAt(), ZoneOffset.UTC))
                .update();
        if (inserted == 0) throw new EmailTakenException();
        return user;
    }

    @Override
    public void linkGoogleSubject(UUID userId, String subject) {
        users.linkGoogleSubject(userId, subject);
    }
}
