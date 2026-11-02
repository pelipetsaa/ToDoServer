package com.pelypets.todolist.server.adapter.in.web;

/** Paths of the public API; identical to {@code ApiRoutes} in the Kotlin client. */
public final class ApiPaths {

    public static final String BASE = "/api/v1";
    public static final String AUTH = BASE + "/auth";
    public static final String ME = BASE + "/me";
    public static final String TODOS = BASE + "/todos";
    public static final String HEALTH = "/health";
    public static final String HEALTH_READY = HEALTH + "/ready";

    private ApiPaths() {}
}
