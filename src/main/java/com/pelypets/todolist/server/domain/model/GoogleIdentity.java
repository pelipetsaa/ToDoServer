package com.pelypets.todolist.server.domain.model;

/** A Google account whose ID token was verified and whose email Google has verified. */
public record GoogleIdentity(String subject, String email) {}
