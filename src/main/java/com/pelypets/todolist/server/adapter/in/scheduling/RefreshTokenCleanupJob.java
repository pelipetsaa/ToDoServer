package com.pelypets.todolist.server.adapter.in.scheduling;

import com.pelypets.todolist.server.application.port.in.MaintenanceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sign-in only purges the expired tokens of the user signing in; this job removes those of users who never come
 * back. Deleting is idempotent, so running it on every instance is harmless.
 */
@Component
class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final MaintenanceUseCase maintenance;

    RefreshTokenCleanupJob(MaintenanceUseCase maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(cron = "${app.jobs.refresh-token-cleanup-cron}")
    void purgeExpiredRefreshTokens() {
        int purged = maintenance.purgeExpiredRefreshTokens();
        if (purged > 0) log.info("Purged {} expired refresh tokens", purged);
    }
}
