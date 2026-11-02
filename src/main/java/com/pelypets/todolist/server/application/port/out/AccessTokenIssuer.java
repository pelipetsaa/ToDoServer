package com.pelypets.todolist.server.application.port.out;

import java.util.UUID;

public interface AccessTokenIssuer {

    String issue(UUID userId);

    long ttlSeconds();
}
