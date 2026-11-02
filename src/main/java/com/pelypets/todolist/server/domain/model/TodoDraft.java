package com.pelypets.todolist.server.domain.model;

import com.pelypets.todolist.server.domain.exception.ValidationException;
import java.time.Instant;

/**
 * The editable fields of a todo. Always valid once constructed: the title is trimmed, and omitted optional fields get
 * their defaults.
 */
public record TodoDraft(String title, String description, Instant dueAt, TodoStatus status) {

    public TodoDraft {
        DomainRules.require(DomainRules.todoError(title, description));
        if (dueAt == null) throw new ValidationException("dueAt is required");
        title = title.strip();
        description = description == null ? "" : description;
        status = status == null ? TodoStatus.TODO : status;
    }
}
