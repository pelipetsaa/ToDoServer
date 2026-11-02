package com.pelypets.todolist.server.adapter.in.web.error;

/** The {@code code} of every error body; clients branch on it, so the values are part of the API. */
public enum ErrorCode {
    VALIDATION_ERROR,
    UNAUTHORIZED,
    NOT_FOUND,
    EMAIL_TAKEN,
    TOO_MANY_REQUESTS,
    INTERNAL_ERROR
}
