package com.example.taskmanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springdoc.core.models.GroupedOpenApi;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;

@Configuration(proxyBeanMethods = false)
@Profile("alpha")
public class OpenApiConfiguration {

    @Bean
    public OpenApiCustomizer alphaOnlyDefaultSpec() {
        return api -> api.getPaths().entrySet().removeIf(entry -> !entry.getKey().equals("/alpha/plan"));
    }

    @Bean
    public OpenAPI alphaOpenApi() {
        var error = new ObjectSchema()
                .description("Problem Detail with a stable code. Graph diagnostics also include taskIds; "
                        + "cycle IDs may include blocked descendants, not only a cycle witness.")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("code", new StringSchema())
                .addProperty("taskIds", new ArraySchema().items(new IntegerSchema().format("int64")))
                .required(List.of("type", "title", "status", "detail", "code"));
        return new OpenAPI().info(new Info().title("ProjectFlow local alpha API").version("0.0.1-alpha")
                .description("Calculates supplied prototype graphs without persistence. No authentication "
                        + "is required for this prototype calculation. Identity API is documented separately "
                        + "at /v3/api-docs/identity; protected projects at /v3/api-docs/projects. "
                        + "Task CRUD is planned."))
                .components(new Components().addSchemas("ApiError", error));
    }

    @Bean
    public GroupedOpenApi sessionOpenApi() {
        return GroupedOpenApi.builder().group("session").pathsToMatch("/api/session/**")
                .addOpenApiCustomizer(api -> {
                    api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/session/"));
                    api.info(new Info().title("ProjectFlow browser session API").version("0.0.1")
                            .description("Secure HttpOnly host-only cookies; no credentials in JSON responses. "
                                    + "Get /api/session/csrf before login and after login/logout. "
                                    + "Mutations require the returned X-XSRF-TOKEN header. "
                                    + "Refresh rotates once without extending the session deadline."));
                    addProtectedErrors(api);
                    api.getPaths().values().forEach(path -> {
                        if (path.getPost() != null) {
                            path.getPost().setSecurity(List.of(new io.swagger.v3.oas.models.security
                                    .SecurityRequirement().addList("csrfAuth")));
                        }
                    });
                }).build();
    }

    @Bean
    public GroupedOpenApi identityOpenApi() {
        return GroupedOpenApi.builder().group("identity").pathsToMatch("/api/auth/**", "/api/users/**")
                .addOpenApiCustomizer(api -> {
                    api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/auth/")
                            && !entry.getKey().startsWith("/api/users/"));
                    api.info(new Info().title("ProjectFlow identity API").version("0.0.1")
                            .description("Registration, rotating refresh sessions, own profile and ADMIN role updates. "
                                    + "Bearer JWT or browser cookie required except register/login/refresh; "
                                    + "cookie mutations require CSRF; alpha storage is separate."));
                    addProtectedErrors(api);
                }).build();
    }

    @Bean
    public GroupedOpenApi projectOpenApi() {
        return GroupedOpenApi.builder().group("projects").pathsToMatch("/api/projects/**")
                .addOpenApiCustomizer(api -> {
                    api.getPaths().entrySet().removeIf(entry -> !entry.getKey().equals("/api/projects")
                            && !entry.getKey().startsWith("/api/projects/"));
                    api.info(new Info().title("ProjectFlow projects API").version("0.0.1")
                            .description("Persisted projects, membership and ownership transfer "
                                    + "with live access checks. "
                                    + "Project statuses remain INITIATED until execution commands are implemented. "
                                    + "Alpha browser storage is separate."));
                    addProtectedErrors(api);
                }).build();
    }

    private static void addProtectedErrors(OpenAPI api) {
        api.getComponents().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
        api.getComponents().addSecuritySchemes("cookieAuth", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE).name("__Host-pf-access"));
        api.getComponents().addSecuritySchemes("csrfAuth", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER).name("X-XSRF-TOKEN"));
        api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            if (operation.getSecurity() != null && operation.getSecurity().stream()
                    .anyMatch(requirement -> requirement.containsKey("bearerAuth"))) {
                var cookie = new io.swagger.v3.oas.models.security.SecurityRequirement().addList("cookieAuth");
                if (operation != path.getGet()) {
                    cookie.addList("csrfAuth");
                }
                operation.addSecurityItem(cookie);
            }
            for (String status : List.of("400", "401", "403", "404", "409")) {
                operation.getResponses().addApiResponse(status, new io.swagger.v3.oas.models.responses
                        .ApiResponse().description("Access, version or request error; inspect the stable code")
                        .content(new io.swagger.v3.oas.models.media.Content().addMediaType(
                                "application/problem+json", new io.swagger.v3.oas.models.media.MediaType()
                                        .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                .$ref("#/components/schemas/ApiError")))));
            }
        }));
    }
}
