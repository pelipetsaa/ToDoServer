package com.pelypets.todolist.server.domain.event;

import java.util.UUID;

/** A refresh token was presented after it had been rotated: someone holds a copy, so its session was revoked. */
public record RefreshTokenReused(UUID familyId) {}
