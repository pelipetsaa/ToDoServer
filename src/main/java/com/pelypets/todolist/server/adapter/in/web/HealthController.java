package com.pelypets.todolist.server.adapter.in.web;

import com.pelypets.todolist.server.adapter.in.web.dto.HealthResponse;
import com.pelypets.todolist.server.application.port.in.MaintenanceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The probes the Ktor server exposed, kept for existing deployments. Kubernetes-style probes are also available at
 * {@code /actuator/health/liveness} and {@code /actuator/health/readiness}.
 */
@RestController
@Tag(name = "ops", description = "Health checks.")
public class HealthController {

    private final MaintenanceUseCase maintenance;

    public HealthController(MaintenanceUseCase maintenance) {
        this.maintenance = maintenance;
    }

    @GetMapping(ApiPaths.HEALTH)
    @Operation(operationId = "health", summary = "Liveness")
    public HealthResponse health() {
        return HealthResponse.UP;
    }

    @GetMapping(ApiPaths.HEALTH_READY)
    @Operation(operationId = "ready", summary = "Readiness")
    public ResponseEntity<HealthResponse> ready() {
        return maintenance.isReady()
                ? ResponseEntity.ok(HealthResponse.UP)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResponse.DOWN);
    }
}
