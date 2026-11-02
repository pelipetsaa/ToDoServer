package com.pelypets.todolist.server.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * An account. It signs in with a password, with Google, or — before Google is linked — with a password only:
 * linking Google removes the password.
 */
public record User(UUID id, String email, String passwordHash, String googleSubject, Instant createdAt) {

    public static User withPassword(String email, String passwordHash, Instant now) {
        return new User(UUID.randomUUID(), email, passwordHash, null, now);
    }

    public static User withGoogle(String email, String googleSubject, Instant now) {
        return new User(UUID.randomUUID(), email, null, googleSubject, now);
    }

    public Optional<String> password() {
        return Optional.ofNullable(passwordHash);
    }

    public boolean isLinkedToGoogle() {
        return googleSubject != null;
    }
}
