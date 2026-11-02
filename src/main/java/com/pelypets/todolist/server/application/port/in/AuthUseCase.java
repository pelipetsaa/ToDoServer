package com.pelypets.todolist.server.application.port.in;

import com.pelypets.todolist.server.domain.model.TokenPair;
import com.pelypets.todolist.server.domain.model.User;
import java.util.UUID;

public interface AuthUseCase {

    TokenPair register(String email, String password);

    TokenPair login(String email, String password);

    TokenPair loginWithGoogle(String idToken);

    /** Single use: the refresh token sent is revoked and a new pair is issued. */
    TokenPair refresh(String refreshToken);

    void logout(String refreshToken);

    User currentUser(UUID userId);
}
