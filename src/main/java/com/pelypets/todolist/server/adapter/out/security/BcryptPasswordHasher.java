package com.pelypets.todolist.server.adapter.out.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategy;
import com.pelypets.todolist.server.application.port.out.PasswordHasher;

/**
 * bcrypt reads at most 72 bytes, which a 128-character or a 40-letter Cyrillic password exceeds; Spring Security's
 * encoder rejects such passwords. Longer passwords are pre-hashed with SHA-512 instead, shorter ones are passed
 * through unchanged. This is the scheme of the Ktor server, so hashes stored by either server verify in both.
 */
public final class BcryptPasswordHasher implements PasswordHasher {

    private static final BCrypt.Version VERSION = BCrypt.Version.VERSION_2A;

    private final int cost;
    private final LongPasswordStrategy longPasswords = LongPasswordStrategies.hashSha512(VERSION);
    /** Verified when there is no real hash, so that case takes as long as a wrong password. */
    private final String dummyHash;

    public BcryptPasswordHasher(int cost) {
        this.cost = cost;
        this.dummyHash = hash("no account has this password");
    }

    @Override
    public String hash(String rawPassword) {
        return BCrypt.with(VERSION, longPasswords).hashToString(cost, rawPassword.toCharArray());
    }

    @Override
    public boolean verify(String rawPassword, String hash) {
        boolean verified = BCrypt.verifyer(VERSION, longPasswords)
                .verify(rawPassword.toCharArray(), hash != null ? hash : dummyHash)
                .verified;
        return hash != null && verified;
    }
}
