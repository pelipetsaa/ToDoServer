package com.pelypets.todolist.server.domain.exception;

/**
 * Business rule violations. The hierarchy is sealed so the web layer can map every case with an exhaustive switch.
 */
public abstract sealed class DomainException extends RuntimeException
        permits ValidationException, UnauthorizedException, NotFoundException, EmailTakenException {

    protected DomainException(String message) {
        super(message);
    }
}
