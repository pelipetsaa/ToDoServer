package com.pelypets.todolist.server.application.port.out;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean verify(String rawPassword, String hash);
}
