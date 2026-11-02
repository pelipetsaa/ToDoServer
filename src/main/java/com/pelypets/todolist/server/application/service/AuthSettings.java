package com.pelypets.todolist.server.application.service;

import java.time.Duration;

/** Settings the auth use cases need, kept free of how the application is configured. */
public record AuthSettings(Duration refreshTokenTtl) {}
