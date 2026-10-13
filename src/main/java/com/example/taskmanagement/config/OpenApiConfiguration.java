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
                        + "at /v3/api-docs/identity; protected project/task CRUD is planned for later sprints."))
                .components(new Components().addSchemas("ApiError", error));
    }

    @Bean
    public GroupedOpenApi identityOpenApi() {
        return GroupedOpenApi.builder().group("identity").pathsToMatch("/api/**")
                .addOpenApiCustomizer(api -> {
                    api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/"));
                    api.info(new Info().title("ProjectFlow identity API").version("0.0.1")
                            .description("Registration, rotating refresh sessions, own profile and ADMIN role updates. "
                                    + "Bearer JWT required except register/login/refresh; alpha storage is separate."));
                    api.getComponents().addSecuritySchemes("bearerAuth", new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
                    api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                        for (String status : List.of("400", "401", "403", "404", "409")) {
                            operation.getResponses().addApiResponse(status, new io.swagger.v3.oas.models.responses
                                    .ApiResponse().description("Identity or request error; inspect the stable code")
                                    .content(new io.swagger.v3.oas.models.media.Content().addMediaType(
                                            "application/problem+json", new io.swagger.v3.oas.models.media.MediaType()
                                                    .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                            .$ref("#/components/schemas/ApiError")))));
                        }
                    }));
                }).build();
    }
}
