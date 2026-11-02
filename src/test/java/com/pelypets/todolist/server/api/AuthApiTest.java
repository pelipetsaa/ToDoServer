package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import com.pelypets.todolist.server.adapter.in.web.error.ErrorCode;
import com.pelypets.todolist.server.domain.model.GoogleIdentity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Ported from the Ktor server's AuthApiTest: the same requests must get the same answers. */
class AuthApiTest extends ApiTest {

    private static final String REGISTER = ApiPaths.AUTH + "/register";
    private static final String LOGIN = ApiPaths.AUTH + "/login";
    private static final String GOOGLE = ApiPaths.AUTH + "/google";
    private static final String REFRESH = ApiPaths.AUTH + "/refresh";
    private static final String LOGOUT = ApiPaths.AUTH + "/logout";

    record User(String id, String email) {}

    @Test
    void healthEndpointIsPublic() {
        Response response = get(ApiPaths.HEALTH, null);

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void registerReturnsTokensAndMeReturnsTheNormalizedEmail() {
        TokenPair tokens = register("  New.User@Example.COM ", "password123");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.expiresInSeconds()).isEqualTo(900);

        Response me = get(ApiPaths.ME, tokens.accessToken());
        assertThat(me.status()).isEqualTo(HttpStatus.OK);
        assertThat(read(me, User.class).email()).isEqualTo("new.user@example.com");
    }

    @Test
    void registerWithAnExistingEmailReturns409() {
        register("dup@example.com", "password123");

        assertError(post(REGISTER, credentials("DUP@example.com", "password123")), HttpStatus.CONFLICT, ErrorCode.EMAIL_TAKEN);
    }

    @Test
    void registerValidatesEmailAndPassword() {
        assertError(post(REGISTER, credentials("not-an-email", "password123")), HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(post(REGISTER, credentials("ok@example.com", "short")), HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void malformedJsonReturns400() {
        assertError(post(LOGIN, Map.of("email", "x")), HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(post(LOGIN, "{not json"), HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void loginSucceedsWithTheRightPasswordAndFailsOtherwise() {
        register("login@example.com", "password123");

        assertThat(post(LOGIN, credentials("login@example.com", "password123")).status()).isEqualTo(HttpStatus.OK);
        assertError(post(LOGIN, credentials("login@example.com", "wrong-password")), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        assertError(post(LOGIN, credentials("nobody@example.com", "password123")), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void passwordsLongerThanBcrypts72BytesCanRegisterAndSignIn() {
        String latin = "a".repeat(128);
        String cyrillic = "пароль".repeat(20); // 120 characters, 240 bytes in UTF-8
        register("latin@example.com", latin);
        register("cyrillic@example.com", cyrillic);

        for (var entry : Map.of("latin@example.com", latin, "cyrillic@example.com", cyrillic).entrySet()) {
            assertThat(post(LOGIN, credentials(entry.getKey(), entry.getValue())).status())
                    .as(entry.getKey())
                    .isEqualTo(HttpStatus.OK);
            // Same first 72 bytes, different tail: bcrypt alone would accept it.
            String almost = entry.getValue().substring(0, entry.getValue().length() - 1) + "x";
            assertError(post(LOGIN, credentials(entry.getKey(), almost)), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
        assertError(post(LOGIN, credentials("latin@example.com", "b".repeat(5_000))), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void signInExpiresOldRefreshTokensOfTheUser() {
        register("cleanup@example.com", "password123");
        jdbc.sql("INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) "
                        + "SELECT gen_random_uuid(), id, 'expired-hash', now() - interval '1 day' FROM users")
                .update();

        post(LOGIN, credentials("cleanup@example.com", "password123"));

        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE expires_at <= now()")).isZero();
        assertThat(count("SELECT count(*) FROM refresh_tokens")).isEqualTo(2);
    }

    @Test
    void protectedEndpointsRequireAValidAccessToken() {
        Response missing = get(ApiPaths.ME, null);
        assertError(missing, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        assertThat(missing.headers().getFirst("WWW-Authenticate")).startsWith("Bearer");
        assertError(get(ApiPaths.ME, "garbage"), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void refreshRotatesTheRefreshTokenAndTheOldOneStopsWorking() {
        TokenPair initial = register();

        Response refreshed = post(REFRESH, Map.of("refreshToken", initial.refreshToken()));
        assertThat(refreshed.status()).isEqualTo(HttpStatus.OK);
        TokenPair pair = read(refreshed, TokenPair.class);
        assertThat(pair.refreshToken()).isNotEqualTo(initial.refreshToken());

        assertThat(get(ApiPaths.ME, pair.accessToken()).status()).isEqualTo(HttpStatus.OK);
        assertError(post(REFRESH, Map.of("refreshToken", initial.refreshToken())), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void refreshIgnoresAStaleAccessTokenSentAlongside() {
        TokenPair initial = register();

        Response refreshed = call(org.springframework.http.HttpMethod.POST, REFRESH,
                Map.of("refreshToken", initial.refreshToken()), "expired-or-garbage");

        assertThat(refreshed.status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        TokenPair tokens = register();

        assertThat(post(LOGOUT, Map.of("refreshToken", tokens.refreshToken())).status()).isEqualTo(HttpStatus.NO_CONTENT);
        assertError(post(REFRESH, Map.of("refreshToken", tokens.refreshToken())), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void googleSignInCreatesAnAccountAndLinksAnExistingEmail() {
        google.identities.put("new-user-token", new GoogleIdentity("g-1", "google@example.com"));
        google.identities.put("existing-user-token", new GoogleIdentity("g-2", "Existing@example.com"));

        Response created = post(GOOGLE, Map.of("idToken", "new-user-token"));
        assertThat(created.status()).isEqualTo(HttpStatus.OK);
        assertThat(me(read(created, TokenPair.class)).email()).isEqualTo("google@example.com");

        String existingId = me(register("existing@example.com", "password123")).id();

        String linkedId = me(read(post(GOOGLE, Map.of("idToken", "existing-user-token")), TokenPair.class)).id();
        assertThat(linkedId).isEqualTo(existingId);

        String againId = me(read(post(GOOGLE, Map.of("idToken", "existing-user-token")), TokenPair.class)).id();
        assertThat(againId).isEqualTo(existingId);
    }

    @Test
    void linkingGoogleRevokesThePasswordAndSessionsOfWhoeverRegisteredTheEmailFirst() {
        google.identities.put("owner-token", new GoogleIdentity("g-owner", "victim@example.com"));
        TokenPair squatter = register("victim@example.com", "squatter-password");

        Response owner = post(GOOGLE, Map.of("idToken", "owner-token"));
        assertThat(owner.status()).isEqualTo(HttpStatus.OK);

        assertError(post(LOGIN, credentials("victim@example.com", "squatter-password")), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        assertError(post(REFRESH, Map.of("refreshToken", squatter.refreshToken())), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        Response ownerRefresh = post(REFRESH, Map.of("refreshToken", read(owner, TokenPair.class).refreshToken()));
        assertThat(ownerRefresh.status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anEmailLinkedToOneGoogleAccountCannotBeTakenOverByAnother() {
        google.identities.put("first", new GoogleIdentity("g-first", "shared@example.com"));
        google.identities.put("second", new GoogleIdentity("g-second", "shared@example.com"));

        assertThat(post(GOOGLE, Map.of("idToken", "first")).status()).isEqualTo(HttpStatus.OK);
        assertError(post(GOOGLE, Map.of("idToken", "second")), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        assertThat(post(GOOGLE, Map.of("idToken", "first")).status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void googleSignInRejectsInvalidTokens() {
        assertError(post(GOOGLE, Map.of("idToken", "unknown")), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        assertError(post(GOOGLE, Map.of("idToken", " ")), HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void everyResponseCarriesARequestId() {
        Response generated = get(ApiPaths.HEALTH, null);
        assertThat(generated.headers().getFirst("X-Request-Id")).isNotBlank();
    }

    private User me(TokenPair tokens) {
        return read(get(ApiPaths.ME, tokens.accessToken()), User.class);
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    private static Map<String, String> credentials(String email, String password) {
        return Map.of("email", email, "password", password);
    }
}
