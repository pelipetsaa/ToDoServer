package com.pelypets.todolist.server.application.service;

import java.time.Duration;

/** Limits that keep one user from loading the server with huge lists or unbounded storage. */
public record TodoSettings(Duration maxListRange, int maxTodosPerUser) {}
