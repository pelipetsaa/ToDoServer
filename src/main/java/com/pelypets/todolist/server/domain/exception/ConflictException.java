package com.pelypets.todolist.server.domain.exception;

/** The resource changed since the client read it. */
public final class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException todo() {
        return new ConflictException("The todo was changed by another request; fetch it and try again");
    }
}
