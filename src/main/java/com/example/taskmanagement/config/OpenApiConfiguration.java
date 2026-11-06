package com.example.taskmanagement.config;

import com.example.taskmanagement.shared.validation.NoNul;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springdoc.core.models.GroupedOpenApi;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;

@Configuration(proxyBeanMethods = false)
@Profile("alpha")
public class OpenApiConfiguration {
    private static final List<String> ASSIGNMENT_PATHS = List.of(
            "/api/auth/register", "/api/auth/login", "/users/me", "/users/{id}/role",
            "/api/users/me", "/api/users/{id}/role", "/api/projects", "/api/projects/{id}",
            "/api/tasks", "/api/tasks/{id}", "/api/tasks/{id}/start", "/api/tasks/{id}/complete",
            "/api/comments", "/api/comments/{id}", "/api/attachments", "/api/attachments/{id}",
            "/api/attachments/{id}/content", "/api/labels", "/api/labels/{id}",
            "/api/tasks/{taskId}/labels", "/api/tasks/{taskId}/labels/{labelId}");

    @Bean
    public GroupedOpenApi assignmentOpenApi() {
        return GroupedOpenApi.builder().group("assignment")
                .pathsToMatch(ASSIGNMENT_PATHS.toArray(String[]::new))
                .addOpenApiCustomizer(api -> {
                    api.getPaths().entrySet().removeIf(entry -> !ASSIGNMENT_PATHS.contains(entry.getKey()));
                    api.info(new Info().title("Task Management assignment API").version("0.0.1")
                            .description("Core registration, users, projects, tasks, comments, attachments and labels. "
                                    + "This is a focused view of the existing backend, not a separate implementation. "
                                    + "Task start/complete commands are included for progress tracking. "
                                    + "Contract differences and remaining requirements are recorded in the public "
                                    + "review/MATE_ASSIGNMENT_REVIEW.md guide."));
                    addTaskBodies(api);
                    addCollaborationBodies(api);
                    api.getPaths().forEach((route, path) -> {
                        if (List.of("/api/auth/register", "/api/projects", "/api/tasks", "/api/comments",
                                "/api/attachments", "/api/labels").contains(route)) {
                            createdResponse(path.getPost());
                        }
                    });
                    addProtectedErrors(api);
                }).build();
    }

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
                        + "Protected tasks are documented at /v3/api-docs/tasks."))
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
        return GroupedOpenApi.builder().group("identity").pathsToMatch("/api/auth/**", "/api/users/**", "/users/**")
                .addOpenApiCustomizer(api -> {
                    api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/auth/")
                            && !entry.getKey().startsWith("/api/users/") && !entry.getKey().startsWith("/users/"));
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
                    api.getPaths().entrySet().removeIf(entry -> entry.getKey().endsWith("/tasks")
                            || planningRoute(entry.getKey()));
                    api.info(new Info().title("ProjectFlow projects API").version("0.0.1")
                            .description("Persisted projects, membership and ownership transfer "
                                    + "with live access checks. "
                                    + "Projects start INITIATED; owner/ADMIN may set informational status through "
                                    + "versioned PUT/PATCH. Omitted/null status is retained; tasks remain writable. "
                                    + "Alpha browser storage is separate."));
                    addProtectedErrors(api);
                }).build();
    }

    @Bean
    public GroupedOpenApi collaborationOpenApi() {
        return GroupedOpenApi.builder().group("collaboration")
                .pathsToMatch("/api/comments/**", "/api/labels/**", "/api/tasks/*/labels/**")
                .addOpenApiCustomizer(api -> {
                    api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/comments")
                            && !entry.getKey().startsWith("/api/labels") && !entry.getKey().contains("/labels"));
                    api.info(new Info().title("ProjectFlow collaboration API").version("0.0.1")
                            .description("Project-scoped plain-text comments and labels with live access checks. "
                                    + "Collaboration metadata does not change task or planning revisions."));
                    addCollaborationBodies(api);
                    addProtectedErrors(api);
                }).build();
    }

    @Bean
    public GroupedOpenApi attachmentOpenApi() {
        return GroupedOpenApi.builder().group("attachments").pathsToMatch("/api/attachments/**")
                .addOpenApiCustomizer(api -> {
                    api.info(new Info().title("ProjectFlow attachments API").version("0.0.1")
                            .description("Task file attachments stored on local disk (max 5 MiB; "
                                    + "pdf/png/jpeg/webp/plain text). Metadata is in PostgreSQL; "
                                    + "uploads do not change task or planning revisions. "
                                    + "Local, Dropbox and personal MEGA. "
                                    + "MEGA references retain their original account across reconnects."));
                    api.getPaths().forEach((route, path) -> {
                        if (route.equals("/api/attachments")) {
                            createdResponse(path.getPost());
                        }
                    });
                    addProtectedErrors(api);
                }).build();
    }

    @Bean
    public GroupedOpenApi notificationOpenApi() {
        return GroupedOpenApi.builder().group("notifications").pathsToMatch("/api/notifications/**")
                .addOpenApiCustomizer(api -> {
                    api.info(new Info().title("ProjectFlow notifications API").version("0.0.1")
                            .description("In-app notifications projected from the transactional outbox. "
                                    + "Background and browser-triggered dispatch share per-event transactions "
                                    + "and durable retry deadlines; failed events do not block later events. "
                                    + "Email and Telegram use their existing preference-aware channels."));
                    addProtectedErrors(api);
                }).build();
    }

    @Bean
    public GroupedOpenApi taskOpenApi() {
        return GroupedOpenApi.builder().group("tasks").pathsToMatch("/api/tasks/**", "/api/projects/*/tasks",
                        "/api/projects/*/execution-order", "/api/projects/*/available-tasks",
                        "/api/projects/*/next-task", "/api/projects/*/gantt",
                        "/api/projects/*/gantt/what-if", "/api/portfolio", "/api/portfolio/workload")
                .addOpenApiCustomizer(api -> {
                    api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/tasks")
                            && !entry.getKey().startsWith("/api/portfolio")
                            && !entry.getKey().endsWith("/tasks") && !planningRoute(entry.getKey()));
                    api.getPaths().entrySet().removeIf(entry -> entry.getKey().contains("/labels"));
                    api.info(new Info().title("ProjectFlow tasks API").version("0.0.1")
                            .description("Persisted tasks and immutable execution history. "
                                    + "Estimate is optional, 15..525600 physical minutes. "
                                    + "Commands use actual predecessor completion, not resource reservations. "
                                    + "Graph queries return coherent inputRevision snapshots and never change facts."));
                    addTaskBodies(api);
                    addProtectedErrors(api);
                }).build();
    }

    @Bean
    public GroupedOpenApi megaOpenApi() {
        return GroupedOpenApi.builder().group("mega").pathsToMatch("/api/mega/**")
                .addOpenApiCustomizer(api -> {
                    api.info(new Info().title("ProjectFlow personal MEGA API").version("0.0.1")
                            .description("Official native SDK. Passwords are not persisted; sessions are encrypted. "
                                    + "Unlink keeps remote files and metadata. "
                                    + "Old files require their original account."));
                    addProtectedErrors(api);
                }).build();
    }

    private static void addCollaborationBodies(OpenAPI api) {
        api.getPaths().forEach((route, path) -> {
            if (!route.startsWith("/api/comments") && !route.startsWith("/api/labels")
                    && !route.contains("/labels")) {
                return;
            }
            if (route.equals("/api/comments") && path.getPost() != null) {
                setTaskBody(path.getPost(), new ObjectSchema()
                        .addProperty("taskId", new IntegerSchema().format("int64")
                                .minimum(java.math.BigDecimal.ONE))
                        .addProperty("text", new StringSchema().minLength(1).maxLength(10000)
                                .pattern(NoNul.PATTERN))
                        .required(List.of("taskId", "text")).additionalProperties(false));
            }
            if (route.equals("/api/labels") && path.getPost() != null) {
                setTaskBody(path.getPost(), labelMetadata(true));
            }
            if (route.equals("/api/labels/{id}") && path.getPut() != null) {
                setTaskBody(path.getPut(), labelMetadata(false));
            }
            if (path.getPost() != null) {
                var response = path.getPost().getResponses().remove("200");
                if (response != null) {
                    path.getPost().getResponses().addApiResponse("201", response);
                }
            }
        });
    }

    private static void addTaskBodies(OpenAPI api) {
        api.getPaths().forEach((route, path) -> {
            if ((!route.startsWith("/api/tasks") && !route.endsWith("/tasks")) || route.contains("/labels")) {
                return;
            }
            boolean collection = route.equals("/api/tasks") || route.endsWith("/tasks");
            if (path.getPost() != null) {
                if (route.endsWith("/dependencies")) {
                    setTaskBody(path.getPost(), new ObjectSchema()
                            .addProperty("dependsOnTaskId", new IntegerSchema().format("int64")
                                    .minimum(java.math.BigDecimal.ONE))
                            .addProperty("inputRevision", versionSchema())
                            .required(List.of("dependsOnTaskId", "inputRevision"))
                            .additionalProperties(false));
                } else {
                    setTaskBody(path.getPost(), collection
                            ? taskMetadata(true, false, route.equals("/api/tasks"))
                            : new ObjectSchema().addProperty("version", versionSchema())
                                    .required(List.of("version")).additionalProperties(false));
                }
                if (collection || route.endsWith("/dependencies")) {
                    var response = path.getPost().getResponses().remove("200");
                    if (response != null) {
                        path.getPost().getResponses().addApiResponse("201", response);
                    }
                }
            }
            if (path.getPut() != null) {
                setTaskBody(path.getPut(), taskMetadata(false, false, false));
            }
            if (path.getPatch() != null) {
                setTaskBody(path.getPatch(), taskMetadata(false, true, false));
            }
        });
    }

    private static void createdResponse(io.swagger.v3.oas.models.Operation operation) {
        if (operation != null) {
            var response = operation.getResponses().remove("200");
            if (response != null) {
                operation.getResponses().addApiResponse("201", response);
            }
        }
    }

    private static boolean planningRoute(String route) {
        return route.endsWith("/execution-order") || route.endsWith("/available-tasks")
                || route.endsWith("/next-task") || route.endsWith("/gantt")
                || route.endsWith("/gantt/what-if");
    }

    private static io.swagger.v3.oas.models.media.Schema<?> labelMetadata(boolean create) {
        var schema = new ObjectSchema()
                .addProperty("name", new StringSchema().minLength(1).maxLength(100).pattern(NoNul.PATTERN)
                        .description("Trimmed name, unique within its project using Locale.ROOT lowercase"))
                .addProperty("color", new StringSchema().pattern("^#[0-9A-Fa-f]{6}$"));
        if (create) {
            schema.addProperty("projectId", new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ONE));
        } else {
            schema.addProperty("version", versionSchema());
        }
        schema.required(List.of(create ? "projectId" : "version", "name", "color"));
        schema.additionalProperties(false);
        return schema;
    }

    private static io.swagger.v3.oas.models.media.Schema<?> versionSchema() {
        return new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ZERO);
    }

    private static ObjectSchema taskMetadata(boolean create, boolean patch, boolean projectRequired) {
        var schema = new ObjectSchema();
        schema.addProperty("projectId", new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ONE));
        schema.addProperty("name", new StringSchema().minLength(1).maxLength(200).pattern(NoNul.PATTERN));
        schema.addProperty("description", new StringSchema().maxLength(10000).nullable(true).pattern(NoNul.PATTERN));
        schema.addProperty("priority", new StringSchema()._enum(List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")));
        schema.addProperty("estimatedMinutes", new IntegerSchema().minimum(java.math.BigDecimal.valueOf(15))
                .maximum(java.math.BigDecimal.valueOf(525600)).nullable(true));
        schema.addProperty("dueDate", new StringSchema().format("date").nullable(true)
                .description("Finite PostgreSQL DATE, -4713-11-24 through +5874897-12-31"));
        schema.addProperty("assigneeId", new IntegerSchema().format("int64")
                .minimum(java.math.BigDecimal.ONE).nullable(true));
        schema.addProperty("bucket", new StringSchema()._enum(List.of("INBOX", "INCUBATOR", "WORK", "ARCHIVE"))
                .description("Workbench column; independent of lifecycle. Create default is INBOX when omitted."));
        var required = new java.util.ArrayList<String>();
        if (!create) {
            schema.addProperty("version", versionSchema());
            required.add("version");
        }
        if (!patch) {
            required.addAll(List.of("name", "priority"));
        }
        if (projectRequired) {
            required.add("projectId");
        }
        schema.required(required);
        schema.additionalProperties(false);
        return schema;
    }

    private static void setTaskBody(io.swagger.v3.oas.models.Operation operation,
            io.swagger.v3.oas.models.media.Schema<?> schema) {
        operation.setRequestBody(new io.swagger.v3.oas.models.parameters.RequestBody().required(true)
                .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                        new io.swagger.v3.oas.models.media.MediaType().schema(schema))));
    }

    private static void addProtectedErrors(OpenAPI api) {
        addTextPatterns(api);
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

    private static void addTextPatterns(OpenAPI api) {
        // Projected PUT/PATCH input records need the same contract as the validated module DTOs.
        Map<String, List<String>> fields = Map.of(
                "ProjectData", List.of("name", "description"),
                "ChangeRequest", List.of("name", "description"),
                "RegisterUser", List.of("firstName", "lastName"),
                "ProfileChange", List.of("firstName", "lastName"),
                "ProfilePatch", List.of("firstName", "lastName"),
                "LoginRequest", List.of("identifier"));
        fields.forEach((name, properties) -> {
            io.swagger.v3.oas.models.media.Schema<?> schema = api.getComponents().getSchemas().get(name);
            if (schema != null && schema.getProperties() != null) {
                properties.forEach(property -> {
                    var text = schema.getProperties().get(property);
                    if (text != null) {
                        text.setPattern(NoNul.PATTERN);
                    }
                });
            }
        });
    }
}
