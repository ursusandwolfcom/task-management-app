package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.dto.ProjectFilter;
import com.example.taskmanagement.dto.TaskFilter;
import com.example.taskmanagement.model.Project;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.service.DeadlineReminderService;
import com.example.taskmanagement.service.ProjectService;
import com.example.taskmanagement.service.TaskService;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.LazyInitializationException;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class JpaRelationshipsTest extends ApiTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(JpaRelationshipsTest.class);
    @Autowired
    private EntityManagerFactory factory;
    @Autowired
    private ProjectService projects;
    @Autowired
    private TaskService tasks;
    @Autowired
    private Environment environment;
    @Autowired
    private DeadlineReminderService reminders;

    @Test
    void relationsStayLazyWhileResponseDtosRemainUsableAfterTransactionCloses() throws Exception {
        var owner = account();
        var member = account();
        long projectId = project(owner, member);
        long labelId = label(owner, "lazy");
        long taskId = json(201, "POST", "/api/tasks", owner.token(),
                taskBody(projectId, member.id(), List.of(labelId))).get("id").asLong();
        var loaded = factory.getPersistenceUnitUtil();
        Task entity;
        try (var manager = factory.createEntityManager()) {
            entity = manager.find(Task.class, taskId);
            assertThat(loaded.isLoaded(entity, "project")).isFalse();
            assertThat(loaded.isLoaded(entity, "assignee")).isFalse();
            assertThat(loaded.isLoaded(entity, "labels")).isFalse();
            var projectEntity = manager.find(Project.class, projectId);
            assertThat(loaded.isLoaded(projectEntity, "owner")).isFalse();
            assertThat(loaded.isLoaded(projectEntity, "members")).isFalse();
        }
        assertThatThrownBy(() -> entity.getLabels().size()).isInstanceOf(LazyInitializationException.class);
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var taskDto = tasks.get(owner.id(), taskId);
        var projectDto = projects.get(owner.id(), projectId);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(taskDto.labelIds()).containsExactly(labelId);
        assertThat(taskDto.assigneeId()).isEqualTo(member.id());
        assertThat(projectDto.memberIds()).containsExactly(member.id());
        assertThat(mapper.writeValueAsString(taskDto)).contains("labelIds").doesNotContain("passwordHash");
        assertThat(mapper.writeValueAsString(projectDto)).contains("memberIds");
        long unassigned = task(owner, projectId, null);
        assertThat(tasks.get(owner.id(), unassigned).assigneeId()).isNull();
    }

    @Test
    void paginatedCollectionMappingUsesBoundedQueriesWithOsivDisabled() throws Exception {
        var owner = account();
        var member = account();
        long projectId = project(owner, member);
        for (int index = 0; index < 12; index++) {
            if (index > 0) {
                project(owner, member);
            }
            long labelId = label(owner, "page-" + index);
            json(201, "POST", "/api/tasks", owner.token(), taskBody(projectId, null, List.of(labelId)));
        }
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            var projectPage = projects.list(owner.id(), new ProjectFilter(null, null, null, null),
                    PageRequest.of(0, 20));
            long projectQueries = statistics.getPrepareStatementCount();
            assertThat(projectPage.getTotalElements()).isEqualTo(12);
            assertThat(projectPage.getContent()).allSatisfy(project ->
                    assertThat(project.memberIds()).containsExactly(member.id()));
            assertThat(projectQueries).as("Project page must not load one member collection per row")
                    .isLessThanOrEqualTo(6);
            statistics.clear();
            var taskPage = tasks.list(owner.id(), projectId,
                    new TaskFilter(null, null, null, null, null, null, null), PageRequest.of(0, 20));
            long taskQueries = statistics.getPrepareStatementCount();
            assertThat(taskPage.getTotalElements()).isEqualTo(12);
            assertThat(taskPage.getContent()).allSatisfy(task -> assertThat(task.labelIds()).hasSize(1));
            assertThat(taskQueries).as("Task page must not load one label collection per row")
                    .isLessThanOrEqualTo(6);
            assertThat(environment.getProperty(
                    "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch", Boolean.class))
                    .isTrue();
            LOG.info("JPA page query counts: projects={}, tasks={}, recordsPerPage=12", projectQueries, taskQueries);
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
            statistics.clear();
        }
    }

    @Test
    void reminderFetchPlanAvoidsSecondaryAssigneeQueries() throws Exception {
        var owner = account();
        var member = account();
        long projectId = project(owner, member);
        var createdTasks = new ArrayList<Long>();
        for (long assigneeId : List.of(owner.id(), member.id())) {
            var body = taskBody(projectId, assigneeId, List.of());
            body.put("dueDate", "2030-02-10");
            createdTasks.add(json(201, "POST", "/api/tasks", owner.token(), body).get("id").asLong());
        }
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            reminders.remind();
            long queries = statistics.getPrepareStatementCount();
            LOG.info("JPA reminder fetch-plan query count: {}", queries);
            assertThat(queries).as("One bounded query should include the assignee data used by reminders")
                    .isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
            statistics.clear();
            for (long id : createdTasks) {
                assertThat(call("DELETE", "/api/tasks/" + id, owner.token(), null).getStatus()).isEqualTo(204);
            }
            assertThat(call("DELETE", "/api/projects/" + projectId, owner.token(), null).getStatus()).isEqualTo(204);
        }
    }

    @Test
    void deletingTaskAndProjectDoesNotCascadeIntoSharedLabelsOrUsers() throws Exception {
        var owner = account();
        var member = account();
        long projectId = project(owner, member);
        long labelId = label(owner, "shared");
        long taskId = json(201, "POST", "/api/tasks", owner.token(),
                taskBody(projectId, null, List.of(labelId))).get("id").asLong();
        json(201, "POST", "/api/comments", member.token(), Map.of("taskId", taskId, "text", "Discussion"));
        assertThat(call("DELETE", "/api/tasks/" + taskId, owner.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("DELETE", "/api/projects/" + projectId, owner.token(), null).getStatus()).isEqualTo(204);
        var label = json(200, "PUT", "/api/labels/" + labelId, owner.token(),
                Map.of("name", "retained-" + owner.name(), "color", "#123456"));
        assertThat(label.get("id").asLong()).isEqualTo(labelId);
        assertThat(users.existsById(owner.id())).isTrue();
        assertThat(users.existsById(member.id())).isTrue();
    }

    private long label(Account owner, String suffix) throws Exception {
        return json(201, "POST", "/api/labels", owner.token(),
                Map.of("name", owner.name() + "-" + suffix, "color", "#123456")).get("id").asLong();
    }
}
