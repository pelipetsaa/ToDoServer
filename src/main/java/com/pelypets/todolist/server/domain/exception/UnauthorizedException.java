package com.pelypets.todolist.server.domain.exception;

public final class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
