package com.pelypets.todolist.server;

import org.springframework.boot.SpringApplication;

/** {@code ./gradlew bootTestRun}: the server against a throwaway PostgreSQL container, no Docker Compose needed. */
public class TestToDoServerApplication {

    public static void main(String[] args) {
        SpringApplication.from(ToDoServerApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
