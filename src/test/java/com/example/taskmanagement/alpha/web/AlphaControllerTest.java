package com.example.taskmanagement.alpha.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlphaController.class)
@ActiveProfiles("alpha")
class AlphaControllerTest {

    private static final String CHAIN = """
            {"tasks":[{"id":1,"priority":"LOW","estimatedMinutes":30},
                      {"id":2,"priority":"CRITICAL"}],
             "dependencies":[{"predecessorId":1,"successorId":2}],
             "completedIds":[],"candidateIds":[1,2]}
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void servesPageAndOnlyWhitelistedAssets() throws Exception {
        mvc.perform(get("/alpha/")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/alpha/styles.css")).andExpect(status().isOk());
        mvc.perform(get("/alpha/app.js")).andExpect(status().isOk());
        mvc.perform(get("/alpha/store.js")).andExpect(status().isOk());
        mvc.perform(get("/alpha/ui.js")).andExpect(status().isOk());
        mvc.perform(get("/alpha/application.properties")).andExpect(status().isNotFound());
        mvc.perform(get("/alpha/.env")).andExpect(status().isNotFound());
        mvc.perform(get("/alpha/types.d.ts")).andExpect(status().isNotFound());
    }

    @Test
    void calculatesOrderAndActualReadiness() throws Exception {
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(CHAIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.executionOrder[0]").value(1))
                .andExpect(jsonPath("$.executionOrder[1]").value(2))
                .andExpect(jsonPath("$.availableIds.length()").value(1))
                .andExpect(jsonPath("$.nextId").value(1));
        String completed = CHAIN.replace("\"completedIds\":[]", "\"completedIds\":[1]")
                .replace("\"candidateIds\":[1,2]", "\"candidateIds\":[2]");
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(completed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableIds[0]").value(2))
                .andExpect(jsonPath("$.nextId").value(2));
    }

    @Test
    void returnsCycleConflictWithoutChangingInput() throws Exception {
        String cyclic = CHAIN.replace("\"dependencies\":[",
                "\"dependencies\":[{\"predecessorId\":2,\"successorId\":1},");
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(cyclic))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CYCLIC_DEPENDENCY"));
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(CHAIN))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidInputsAndFractionalMinutes() throws Exception {
        for (String request : new String[] {"{}", CHAIN.replace("\"id\":1", "\"id\":0"),
                CHAIN.replace("\"LOW\"", "\"UNKNOWN\""),
                CHAIN.replace("\"estimatedMinutes\":30", "\"estimatedMinutes\":0"),
                CHAIN.replace("\"estimatedMinutes\":30", "\"estimatedMinutes\":1.5"),
                CHAIN.replace("\"id\":1", "\"id\":1.5")}) {
            mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsCrossGraphFactsAndUnknownDependencies() throws Exception {
        String unknownFact = CHAIN.replace("\"completedIds\":[]", "\"completedIds\":[3]");
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(unknownFact))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_TASK"));
        String unknownEdge = CHAIN.replace("\"successorId\":2", "\"successorId\":3");
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content(unknownEdge))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_TASK"));
    }

    @Test
    void supportsEmptyProjectAndEmptyNext() throws Exception {
        mvc.perform(post("/alpha/plan").contentType(MediaType.APPLICATION_JSON).content("""
                {"tasks":[],"dependencies":[],"completedIds":[],"candidateIds":[]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.executionOrder.length()").value(0))
                .andExpect(jsonPath("$.availableIds.length()").value(0))
                .andExpect(jsonPath("$.nextId").isEmpty());
    }
}
