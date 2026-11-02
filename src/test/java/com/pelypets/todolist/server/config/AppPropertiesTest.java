package com.pelypets.todolist.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    @Test
    void developmentFallsBackToTheBuiltInKey() {
        assertThat(AppProperties.resolveJwtSecret("", AppEnvironment.DEVELOPMENT)).isEqualTo(AppProperties.DEV_JWT_SECRET);
        assertThat(AppProperties.resolveJwtSecret(null, AppEnvironment.DEVELOPMENT)).isEqualTo(AppProperties.DEV_JWT_SECRET);
    }

    @Test
    void productionRequiresARealKey() {
        assertThatThrownBy(() -> AppProperties.resolveJwtSecret(" ", AppEnvironment.PRODUCTION))
                .hasMessageContaining("must be set");
        assertThatThrownBy(() -> AppProperties.resolveJwtSecret(AppProperties.DEV_JWT_SECRET, AppEnvironment.PRODUCTION))
                .hasMessageContaining("development key");
        String real = "a-real-production-secret-of-32-chars!";
        assertThat(AppProperties.resolveJwtSecret(real, AppEnvironment.PRODUCTION)).isEqualTo(real);
    }

    @Test
    void shortKeysAreRejectedEverywhere() {
        assertThatThrownBy(() -> AppProperties.resolveJwtSecret("too-short", AppEnvironment.DEVELOPMENT))
                .hasMessageContaining("at least 32");
    }

    @Test
    void googleClientIdsAreTrimmedAndBlanksDropped() {
        assertThat(new AppProperties.Google(java.util.List.of(" a ", "", "b")).clientIds()).containsExactly("a", "b");
    }
}
