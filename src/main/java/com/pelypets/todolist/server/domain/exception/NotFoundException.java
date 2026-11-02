package com.pelypets.todolist.server.domain.exception;

public final class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException todo() {
        return new NotFoundException("Todo not found");
    }
}
