package com.pelypets.todolist.server.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.pelypets.todolist.server.adapter.out.security.BcryptPasswordHasher;
import com.pelypets.todolist.server.adapter.out.security.SecureRefreshTokenGenerator;
import org.junit.jupiter.api.Test;

class SecurityAdaptersTest {

    @Test
    void bcryptHashesAreInTheKtorServersFormat() {
        var hasher = new BcryptPasswordHasher(4);
        String hash = hasher.hash("password123");

        assertThat(hash).startsWith("$2a$04$");
        assertThat(hasher.verify("password123", hash)).isTrue();
        assertThat(hasher.verify("password124", hash)).isFalse();
    }

    @Test
    void refreshTokensAreRandomAndStoredAsHexSha256() {
        var generator = new SecureRefreshTokenGenerator();
        String token = generator.generate();

        assertThat(token).hasSize(43).isNotEqualTo(generator.generate());
        assertThat(generator.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
