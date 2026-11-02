package com.pelypets.todolist.server.application.service;

import com.pelypets.todolist.server.application.port.in.MaintenanceUseCase;
import com.pelypets.todolist.server.application.port.out.DatabaseProbe;
import com.pelypets.todolist.server.application.port.out.RefreshTokenRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService implements MaintenanceUseCase {

    private final DatabaseProbe database;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public MaintenanceService(DatabaseProbe database, RefreshTokenRepository refreshTokens, Clock clock) {
        this.database = database;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Override
    public boolean isReady() {
        return database.isReachable();
    }

    @Override
    @Transactional
    public int purgeExpiredRefreshTokens() {
        return refreshTokens.deleteExpired(clock.instant());
    }
}
