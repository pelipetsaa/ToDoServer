package com.pelypets.todolist.server.application.port.out;

public interface PasswordHasher {

    String hash(String rawPassword);

    /**
     * False when {@code hash} is null. That case still costs as much as a real check, so the response time does not
     * tell whether an account exists or has a password.
     */
    boolean verify(String rawPassword, String hash);
}
