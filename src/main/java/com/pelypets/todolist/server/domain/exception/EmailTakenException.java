package com.pelypets.todolist.server.domain.exception;

public final class EmailTakenException extends DomainException {

    public EmailTakenException() {
        super("An account with this email already exists");
    }
}
