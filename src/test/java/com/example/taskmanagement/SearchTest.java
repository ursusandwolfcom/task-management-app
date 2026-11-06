package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchTest extends ApiTestSupport {
    @Test
    void sqlWildcardCharactersAreLiteralInProjectAndTaskNames() throws Exception {
        var owner = account();
        long project = project(owner);
        project(owner);
        var projectUpdate = new java.util.HashMap<>(projectBody(List.of()));
        projectUpdate.put("name", "Progress_100%\\done");
        json(200, "PUT", "/api/projects/" + project, owner.token(), projectUpdate);
        var taskBody = taskBody(project, null, List.of());
        taskBody.put("name", "Progress_100%\\done");
        long task = json(201, "POST", "/api/tasks", owner.token(), taskBody).get("id").asLong();
        task(owner, project, null);
        for (String term : List.of("%", "_", "\\")) {
            var projectResponse = mvc.perform(get("/api/projects").param("name", term)
                    .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
            assertThat(projectResponse.getStatus()).isEqualTo(200);
            var projects = mapper.readTree(projectResponse.getContentAsString()).get("content");
            assertThat(projects.size()).isEqualTo(1);
            assertThat(projects.get(0).get("id").asLong()).isEqualTo(project);
            var taskResponse = mvc.perform(get("/api/tasks").param("projectId", "" + project).param("name", term)
                    .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
            assertThat(taskResponse.getStatus()).isEqualTo(200);
            var tasks = mapper.readTree(taskResponse.getContentAsString()).get("content");
            assertThat(tasks.size()).isEqualTo(1);
            assertThat(tasks.get(0).get("id").asLong()).isEqualTo(task);
        }
    }

    @Test
    void projectSearchCombinesFiltersWithoutLeakingOtherProjects() throws Exception {
        var owner = account();
        var member = account();
        var outsider = account();
        long project = project(owner, member);
        var update = new java.util.HashMap<>(projectBody(List.of(member.id())));
        update.put("name", "Milestone_100% API");
        update.put("status", "IN_PROGRESS");
        json(200, "PUT", "/api/projects/" + project, owner.token(), update);
        String filter = "/api/projects?name=milestone&status=IN_PROGRESS&startFrom=2030-01-01&endTo=2030-12-31";
        var results = json(200, "GET", filter, member.token(), null).get("content");
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("id").asLong()).isEqualTo(project);
        assertThat(json(200, "GET", filter, outsider.token(), null).get("content").size()).isZero();
        assertThat(json(200, "GET", filter.replace("IN_PROGRESS", "COMPLETED"), owner.token(), null)
                .get("content").size()).isZero();
        assertThat(call("GET", "/api/projects?startFrom=2031-01-01&endTo=2030-01-01", owner.token(), null)
                .getStatus()).isEqualTo(400);
        assertThat(call("GET", "/api/projects?status=INVALID", owner.token(), null).getStatus()).isEqualTo(400);
        assertThat(call("GET", "/api/projects?sort=unknownField", owner.token(), null).getStatus()).isEqualTo(400);
        assertThat(json(200, "GET", "/api/projects", owner.token(), null).get("size").asInt()).isEqualTo(20);
        assertThat(json(200, "GET", "/api/projects?size=1000", owner.token(), null).get("size").asInt())
                .isEqualTo(100);
    }

    @Test
    void taskSearchCombinesLabelPriorityStatusAssigneeAndInclusiveDates() throws Exception {
        var owner = account();
        long project = project(owner);
        long otherProject = project(owner);
        long label = json(201, "POST", "/api/labels", owner.token(),
                Map.of("name", "search-" + owner.name(), "color", "#112233")).get("id").asLong();
        var body = taskBody(project, owner.id(), List.of(label));
        body.put("name", "Search target");
        body.put("dueDate", "2030-04-01");
        body.put("priority", "HIGH");
        long task = json(201, "POST", "/api/tasks", owner.token(), body).get("id").asLong();
        task(owner, project, null);
        String filter = "/api/tasks?projectId=" + project + "&name=TARGET&status=NOT_STARTED&priority=HIGH"
                + "&assigneeId=" + owner.id() + "&labelId=" + label + "&dueFrom=2030-04-01&dueTo=2030-04-01";
        var results = json(200, "GET", filter, owner.token(), null).get("content");
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("id").asLong()).isEqualTo(task);
        assertThat(json(200, "GET", filter.replace("HIGH", "LOW"), owner.token(), null)
                .get("content").size()).isZero();
        assertThat(json(200, "GET", filter.replace("projectId=" + project, "projectId=" + otherProject),
                owner.token(), null).get("content").size()).isZero();
        assertThat(call("GET", "/api/tasks?projectId=" + project + "&dueFrom=2031-01-01&dueTo=2030-01-01",
                owner.token(), null).getStatus()).isEqualTo(400);
        assertThat(call("GET", "/api/tasks?projectId=" + project + "&dueFrom=invalid", owner.token(), null)
                .getStatus()).isEqualTo(400);
    }
}
