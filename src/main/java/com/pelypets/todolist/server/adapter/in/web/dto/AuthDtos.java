package com.pelypets.todolist.server.adapter.in.web.dto;

import com.pelypets.todolist.server.domain.model.TokenPair;
import com.pelypets.todolist.server.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request and response bodies of the auth endpoints, as in the Kotlin {@code core} module of the client. */
public final class AuthDtos {

    private AuthDtos() {}

    @Schema(name = "EmailCredentials")
    public record EmailCredentials(
            @Schema(description = "Case-insensitive; stored lower-cased", maxLength = 254, format = "email")
            @NotNull(message = "email is required") String email,
            @Schema(minLength = 8, maxLength = 128)
            @NotNull(message = "password is required") String password) {}

    @Schema(name = "GoogleLogin")
    public record GoogleLogin(
            @Schema(description = "Google ID token issued for the web OAuth client")
            @NotBlank(message = "idToken must not be blank") String idToken) {}

    @Schema(name = "RefreshToken")
    public record RefreshTokenBody(@NotBlank(message = "refreshToken must not be blank") String refreshToken) {}

    @Schema(name = "TokenPair")
    public record TokenPairResponse(
            @Schema(description = "JWT to send as `Authorization: Bearer ...`") String accessToken,
            String refreshToken,
            @Schema(example = "900") long expiresInSeconds) {

        public static TokenPairResponse from(TokenPair pair) {
            return new TokenPairResponse(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds());
        }
    }

    @Schema(name = "User")
    public record UserResponse(@Schema(format = "uuid") String id, @Schema(format = "email") String email) {

        public static UserResponse from(User user) {
            return new UserResponse(user.id().toString(), user.email());
        }
    }
}
