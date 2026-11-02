package com.pelypets.todolist.server.application.port.in;

public interface MaintenanceUseCase {

    /** True when the server can serve requests, which needs the database. */
    boolean isReady();

    /** Deletes refresh tokens that expired; returns how many. */
    int purgeExpiredRefreshTokens();
}
