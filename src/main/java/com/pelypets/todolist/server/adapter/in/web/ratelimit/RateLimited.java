package com.pelypets.todolist.server.adapter.in.web.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits the endpoint per client IP with the auth budget ({@code app.rate-limit.auth-requests-per-minute}), shared
 * by every endpoint carrying the annotation, to slow down password guessing.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {}
