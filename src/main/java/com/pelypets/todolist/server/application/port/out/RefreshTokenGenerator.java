package com.pelypets.todolist.server.application.port.out;

public interface RefreshTokenGenerator {

    String generate();

    String hash(String token);
}
