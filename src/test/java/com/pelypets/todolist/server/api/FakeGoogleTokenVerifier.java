package com.pelypets.todolist.server.api;

import com.pelypets.todolist.server.application.port.out.GoogleTokenVerifier;
import com.pelypets.todolist.server.domain.model.GoogleIdentity;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Accepts exactly the tokens a test registered. */
public class FakeGoogleTokenVerifier implements GoogleTokenVerifier {

    final Map<String, GoogleIdentity> identities = new ConcurrentHashMap<>();

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        return Optional.ofNullable(identities.get(idToken));
    }
}
