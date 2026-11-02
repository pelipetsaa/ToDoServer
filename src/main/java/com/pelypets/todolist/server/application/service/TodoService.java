package com.pelypets.todolist.server.application.service;

import com.pelypets.todolist.server.application.port.in.TodoUseCase;
import com.pelypets.todolist.server.application.port.out.TodoRepository;
import com.pelypets.todolist.server.domain.event.TodoCreated;
import com.pelypets.todolist.server.domain.exception.ConflictException;
import com.pelypets.todolist.server.domain.exception.NotFoundException;
import com.pelypets.todolist.server.domain.exception.ValidationException;
import com.pelypets.todolist.server.domain.model.Todo;
import com.pelypets.todolist.server.domain.model.TodoDraft;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TodoService implements TodoUseCase {

    private final TodoRepository todos;
    private final ApplicationEventPublisher events;
    private final TodoSettings settings;
    private final Clock clock;

    public TodoService(TodoRepository todos, ApplicationEventPublisher events, TodoSettings settings, Clock clock) {
        this.todos = todos;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> list(UUID userId, Instant from, Instant to) {
        if (!from.isBefore(to)) throw new ValidationException("'from' must be before 'to'");
        if (Duration.between(from, to).compareTo(settings.maxListRange()) > 0) {
            throw new ValidationException("The range must be at most " + settings.maxListRange().toDays() + " days");
        }
        return todos.findDueBetween(userId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public Todo get(UUID userId, UUID todoId) {
        return todos.findById(userId, todoId).orElseThrow(NotFoundException::todo);
    }

    @Override
    public Todo create(UUID userId, TodoDraft draft) {
        // Concurrent creates can overshoot the limit by a few; it bounds storage, it is not a quota.
        if (todos.countForUser(userId) >= settings.maxTodosPerUser()) {
            throw new ValidationException("A user can have at most " + settings.maxTodosPerUser() + " todos");
        }
        Todo todo = todos.insert(Todo.create(userId, draft, clock.instant()));
        events.publishEvent(new TodoCreated(userId, todo.id()));
        return todo;
    }

    @Override
    public Todo update(UUID userId, UUID todoId, TodoDraft draft, OptionalLong expectedVersion) {
        Todo current = get(userId, todoId);
        if (expectedVersion.isPresent() && expectedVersion.getAsLong() != current.version()) {
            throw ConflictException.todo();
        }
        return todos.update(current.apply(draft, clock.instant())).orElseThrow(() -> todos.findById(userId, todoId).isPresent()
                ? ConflictException.todo()
                : NotFoundException.todo());
    }

    @Override
    public void delete(UUID userId, UUID todoId) {
        if (!todos.delete(userId, todoId)) throw NotFoundException.todo();
    }
}
