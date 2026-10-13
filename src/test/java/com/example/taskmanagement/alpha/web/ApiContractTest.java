package com.example.taskmanagement.alpha.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

@WebMvcTest(AlphaController.class)
@ActiveProfiles("alpha")
@Import(AlphaSliceSecurity.class)
class ApiContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void malformedJsonAndInvalidDatesReturnSafeProblemDetails() throws Exception {
        for (String body : List.of("{", """
                {"tasks":[{"id":1,"priority":"LOW","dueDate":"2026-02-29"}],
                 "dependencies":[],"completedIds":[],"candidateIds":[1]}
                """)) {
            mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                    .andExpect(jsonPath("$.instance").value("/alpha/plan"));
        }
    }

    @Test
    void missingAndNullRequiredFieldsReturnValidationProblem() throws Exception {
        for (String body : List.of("{}", """
                {"tasks":[null],"dependencies":[],"completedIds":[],"candidateIds":[]}
                """)) {
            mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void duplicateTaskAndConflictingExecutionFactsKeepGraphCodesAndIds() throws Exception {
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content("""
                {"tasks":[{"id":1,"priority":"LOW"},{"id":1,"priority":"HIGH"}],
                 "dependencies":[],"completedIds":[],"candidateIds":[]}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_TASK"))
                .andExpect(jsonPath("$.taskIds[0]").value(1));
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content("""
                {"tasks":[{"id":1,"priority":"LOW"}],
                 "dependencies":[],"completedIds":[1],"candidateIds":[1]}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXECUTION_FACTS"))
                .andExpect(jsonPath("$.taskIds[0]").value(1));
    }

    @Test
    void cycleProblemKeepsLegacyFieldsAndUnresolvedDescendants() throws Exception {
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content("""
                {"tasks":[{"id":1,"priority":"LOW"},{"id":2,"priority":"LOW"},
                          {"id":3,"priority":"LOW"}],
                 "dependencies":[{"predecessorId":1,"successorId":2},
                                 {"predecessorId":2,"successorId":1},
                                 {"predecessorId":2,"successorId":3}],
                 "completedIds":[],"candidateIds":[1,2,3]}
                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409)).andExpect(jsonPath("$.code").value("CYCLIC_DEPENDENCY"))
                .andExpect(jsonPath("$.taskIds.length()").value(3)).andExpect(jsonPath("$.taskIds[2]").value(3));
    }

    @Test
    void safeIntegerAndTaskCountBoundariesAreEnforced() throws Exception {
        String tasks = IntStream.rangeClosed(1, 1000)
                .mapToObj(id -> "{\"id\":" + id + ",\"priority\":\"LOW\"}").collect(Collectors.joining(","));
        String prefix = "{\"tasks\":[";
        String suffix = "],\"dependencies\":[],\"completedIds\":[],\"candidateIds\":[]}";
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(prefix + tasks + suffix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.executionOrder.length()").value(1000));
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON)
                .content(prefix + tasks + ",{\"id\":1001,\"priority\":\"LOW\"}" + suffix))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON)
                .content(prefix + "{\"id\":9007199254740992,\"priority\":\"LOW\"}" + suffix))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unsupportedMethodAndMediaTypeUseTheSameSafeErrorRepresentation() throws Exception {
        mvc.perform(get("/alpha/plan")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/alpha/plan").contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
