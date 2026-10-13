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

@Configuration(proxyBeanMethods = false)
@Profile("alpha")
public class OpenApiConfiguration {

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
                        + "is implemented. Registration, protected CRUD and execution endpoints are planned "
                        + "for later sprints and are not exposed by this specification."))
                .components(new Components().addSchemas("ApiError", error));
    }
}
