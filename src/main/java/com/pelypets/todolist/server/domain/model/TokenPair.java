package com.pelypets.todolist.server.domain.model;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
