package com.pelypets.todolist.server.domain.exception;

public final class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(message);
    }
}
