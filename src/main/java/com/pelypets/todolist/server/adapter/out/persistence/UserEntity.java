package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Read model of a user; users are inserted by {@link UserPersistenceAdapter} with plain SQL. */
@Entity
@Table(name = "users")
class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "google_subject", unique = true)
    private String googleSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserEntity() {}

    User toDomain() {
        return new User(id, email, passwordHash, googleSubject, createdAt);
    }
}
