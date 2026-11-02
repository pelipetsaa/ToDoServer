package com.pelypets.todolist.server.adapter.out.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.pelypets.todolist.server.application.port.out.GoogleTokenVerifier;
import com.pelypets.todolist.server.domain.model.GoogleIdentity;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks the signature against Google's cached public keys, the audience against our OAuth client IDs, and expiry. */
public class GoogleApiTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleApiTokenVerifier.class);

    private final List<String> clientIds;
    private final GoogleIdTokenVerifier verifier;

    public GoogleApiTokenVerifier(List<String> clientIds) {
        this.clientIds = List.copyOf(clientIds);
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(this.clientIds)
                .build();
    }

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        if (clientIds.isEmpty()) {
            log.warn("Google sign-in rejected: GOOGLE_CLIENT_IDS is not configured");
            return Optional.empty();
        }
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            log.info("Google ID token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
        if (token == null) return Optional.empty();

        GoogleIdToken.Payload payload = token.getPayload();
        if (payload.getEmail() == null || !Boolean.TRUE.equals(payload.getEmailVerified())) return Optional.empty();
        return Optional.of(new GoogleIdentity(payload.getSubject(), payload.getEmail()));
    }
}
