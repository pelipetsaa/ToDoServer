package com.pelypets.todolist.server.config;

import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import com.pelypets.todolist.server.adapter.in.web.error.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The spec is generated from the controllers; Swagger UI is served at {@code /docs}. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ToDo API")
                        .version("1")
                        .description("""
                                REST API of the ToDo app. Clients authenticate with a short-lived JWT access token \
                                (15 minutes) and rotate it with a refresh token (30 days, single use).

                                Every error has the body `{"code": "...", "message": "..."}`. A todo that belongs to \
                                another user is reported as `NOT_FOUND`. Every response carries an `X-Request-Id` \
                                header; send your own to correlate logs."""))
                .servers(List.of(
                        new Server().url("https://todolist-server-zyck.onrender.com").description("Production"),
                        new Server().url("http://localhost:8080").description("Local development")))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    /** Documents the shared error body as the default response of every API operation. */
    @Bean
    OpenApiCustomizer errorResponses() {
        return openApi -> {
            ModelConverters.getInstance().read(ApiError.class).forEach(openApi.getComponents()::addSchemas);
            var error = new ApiResponse()
                    .description("Error; `code` is one of VALIDATION_ERROR (400), UNAUTHORIZED (401), NOT_FOUND (404), "
                            + "EMAIL_TAKEN (409), TOO_MANY_REQUESTS (429, see `Retry-After`), INTERNAL_ERROR (500).")
                    .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/Error"))));
            openApi.getPaths().forEach((path, item) -> {
                if (path.startsWith(ApiPaths.BASE)) {
                    item.readOperations().forEach(operation -> operation.getResponses().addApiResponse("default", error));
                }
            });
        };
    }
}
