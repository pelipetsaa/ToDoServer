package com.pelypets.todolist.server.application.port.out;

import com.pelypets.todolist.server.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSubject(String subject);

    /** @throws com.pelypets.todolist.server.domain.exception.EmailTakenException when the email or Google account is taken */
    User create(User user);

    /** Links the Google account and removes the password. */
    void linkGoogleSubject(UUID userId, String subject);
}
