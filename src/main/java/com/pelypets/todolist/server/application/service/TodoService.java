package com.pelypets.todolist.server.application.service;

import com.pelypets.todolist.server.application.port.in.TodoUseCase;
import com.pelypets.todolist.server.application.port.out.TodoRepository;
import com.pelypets.todolist.server.domain.event.TodoCreated;
import com.pelypets.todolist.server.domain.exception.NotFoundException;
import com.pelypets.todolist.server.domain.exception.ValidationException;
import com.pelypets.todolist.server.domain.model.Todo;
import com.pelypets.todolist.server.domain.model.TodoDraft;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TodoService implements TodoUseCase {

    private final TodoRepository todos;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TodoService(TodoRepository todos, ApplicationEventPublisher events, Clock clock) {
        this.todos = todos;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> list(UUID userId, Instant from, Instant to) {
        if (!from.isBefore(to)) throw new ValidationException("'from' must be before 'to'");
        return todos.findDueBetween(userId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public Todo get(UUID userId, UUID todoId) {
        return todos.findById(userId, todoId).orElseThrow(NotFoundException::todo);
    }

    @Override
    public Todo create(UUID userId, TodoDraft draft) {
        Todo todo = todos.insert(Todo.create(userId, draft, clock.instant()));
        events.publishEvent(new TodoCreated(userId, todo.id()));
        return todo;
    }

    @Override
    public Todo update(UUID userId, UUID todoId, TodoDraft draft) {
        Todo updated = get(userId, todoId).apply(draft, clock.instant());
        return todos.update(updated).orElseThrow(NotFoundException::todo);
    }

    @Override
    public void delete(UUID userId, UUID todoId) {
        if (!todos.delete(userId, todoId)) throw NotFoundException.todo();
    }
}
