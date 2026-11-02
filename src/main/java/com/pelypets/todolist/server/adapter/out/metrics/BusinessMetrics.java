package com.pelypets.todolist.server.adapter.out.metrics;

import com.pelypets.todolist.server.domain.event.RefreshTokenReused;
import com.pelypets.todolist.server.domain.event.TodoCreated;
import com.pelypets.todolist.server.domain.event.UserRegistered;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Counts business events once their transaction has committed, so rolled-back work is not counted. */
@Component
class BusinessMetrics {

    private final MeterRegistry registry;

    BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener
    void on(UserRegistered event) {
        registry.counter("todo.users.registered", "method", event.method().name().toLowerCase(Locale.ROOT)).increment();
    }

    @TransactionalEventListener
    void on(TodoCreated event) {
        registry.counter("todo.todos.created").increment();
    }

    /** Worth an alert: a non-zero rate means refresh tokens are being stolen. */
    @TransactionalEventListener
    void on(RefreshTokenReused event) {
        registry.counter("todo.refresh_tokens.reused").increment();
    }
}
