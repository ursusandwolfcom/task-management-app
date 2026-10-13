package com.example.taskmanagement.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "alpha"})
class OpenApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void documentsOnlyExistingAlphaCalculationWithItsErrorSchema() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("ProjectFlow local alpha API"))
                .andExpect(jsonPath("$.paths.length()").value(1))
                .andExpect(jsonPath("$.paths['/alpha/plan'].post").exists())
                .andExpect(jsonPath("$.paths['/alpha/plan'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/alpha/plan'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/alpha/plan'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/alpha/plan'].post.responses['200'].content['application/json']"
                        + ".schema['$ref']").value("#/components/schemas/PlanResponse"))
                .andExpect(jsonPath("$.paths['/alpha/plan'].post.responses['409'].content['application/problem+json']"
                        + ".schema['$ref']").value("#/components/schemas/ApiError"))
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.taskIds").exists())
                .andExpect(jsonPath("$.components.securitySchemes").doesNotExist())
                .andExpect(jsonPath("$.security").doesNotExist());
    }

    @Test
    void swaggerUsesLocalSpecificationAndBundledAssets() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }
}
