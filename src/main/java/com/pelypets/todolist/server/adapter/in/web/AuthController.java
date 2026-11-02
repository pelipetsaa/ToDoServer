package com.pelypets.todolist.server.adapter.in.web;

import com.pelypets.todolist.server.adapter.in.web.dto.AuthDtos.EmailCredentials;
import com.pelypets.todolist.server.adapter.in.web.dto.AuthDtos.GoogleLogin;
import com.pelypets.todolist.server.adapter.in.web.dto.AuthDtos.RefreshTokenBody;
import com.pelypets.todolist.server.adapter.in.web.dto.AuthDtos.TokenPairResponse;
import com.pelypets.todolist.server.adapter.in.web.dto.AuthDtos.UserResponse;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.AccountRateLimiter;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.RateLimited;
import com.pelypets.todolist.server.adapter.in.web.security.CurrentUserId;
import com.pelypets.todolist.server.application.port.in.AuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "auth", description = "Sign-up, sign-in and token rotation. Register, login and Google sign-in are rate limited "
        + "per client IP; refresh and logout are not limited.")
public class AuthController {

    private final AuthUseCase auth;
    private final AccountRateLimiter accountLimiter;

    public AuthController(AuthUseCase auth, AccountRateLimiter accountLimiter) {
        this.auth = auth;
        this.accountLimiter = accountLimiter;
    }

    @PostMapping(ApiPaths.AUTH + "/register")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimited
    @Operation(operationId = "register", summary = "Create an account")
    public TokenPairResponse register(@Valid @RequestBody EmailCredentials body) {
        return TokenPairResponse.from(auth.register(body.email(), body.password()));
    }

    @PostMapping(ApiPaths.AUTH + "/login")
    @RateLimited
    @Operation(operationId = "login", summary = "Sign in with email and password",
            description = "Rate limited per client IP and, separately, per account.")
    public TokenPairResponse login(@Valid @RequestBody EmailCredentials body) {
        accountLimiter.consume(body.email());
        return TokenPairResponse.from(auth.login(body.email(), body.password()));
    }

    @PostMapping(ApiPaths.AUTH + "/google")
    @RateLimited
    @Operation(operationId = "loginWithGoogle", summary = "Sign in with a Google ID token", description = """
            Creates the account on first sign-in and links it to an existing email account. Linking removes that \
            account's password and revokes its refresh tokens, because email sign-up does not prove ownership of the \
            address. An email already linked to a different Google account is rejected with `UNAUTHORIZED`.""")
    public TokenPairResponse loginWithGoogle(@Valid @RequestBody GoogleLogin body) {
        return TokenPairResponse.from(auth.loginWithGoogle(body.idToken()));
    }

    /** Not rate limited: the token is a random 256-bit value, and a throttled refresh would sign the user out. */
    @PostMapping(ApiPaths.AUTH + "/refresh")
    @Operation(operationId = "refresh", summary = "Rotate the refresh token",
            description = "Returns a new token pair; the refresh token sent is revoked.")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshTokenBody body) {
        return TokenPairResponse.from(auth.refresh(body.refreshToken()));
    }

    @PostMapping(ApiPaths.AUTH + "/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "logout", summary = "Revoke a refresh token")
    public void logout(@Valid @RequestBody RefreshTokenBody body) {
        auth.logout(body.refreshToken());
    }

    @GetMapping(ApiPaths.ME)
    @Operation(operationId = "me", summary = "Current user", security = @SecurityRequirement(name = "bearerAuth"))
    public UserResponse me(@CurrentUserId UUID userId) {
        return UserResponse.from(auth.currentUser(userId));
    }

    @DeleteMapping(ApiPaths.ME)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAccount", summary = "Delete the account", security = @SecurityRequirement(name = "bearerAuth"),
            description = "Deletes the account with its todos and sessions. Access tokens already issued expire within minutes.")
    public void deleteAccount(@CurrentUserId UUID userId) {
        auth.deleteAccount(userId);
    }
}
