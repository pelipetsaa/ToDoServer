package com.pelypets.todolist.server.domain.model;

/**
 * A Google account whose ID token was verified and whose email Google has verified. {@code emailAuthoritative} is true
 * only when Google manages the mailbox itself (Gmail or a Workspace domain): for any other address Google merely saw
 * a confirmation link clicked once, which is not enough to take over an existing account with that email.
 */
public record GoogleIdentity(String subject, String email, boolean emailAuthoritative) {}
