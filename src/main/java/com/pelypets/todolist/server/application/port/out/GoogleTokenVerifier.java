package com.pelypets.todolist.server.application.port.out;

import com.pelypets.todolist.server.domain.model.GoogleIdentity;
import java.util.Optional;

public interface GoogleTokenVerifier {

    /** Empty when the token is invalid, issued for another client, or the email is not verified. */
    Optional<GoogleIdentity> verify(String idToken);
}
