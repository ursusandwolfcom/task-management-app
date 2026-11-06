package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.taskmanagement.repository.UserRepository;
import com.example.taskmanagement.service.DropboxService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiTestSupport {
    protected record Account(long id, String name, String token) {
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper mapper;
    @Autowired
    protected UserRepository users;
    @MockitoBean
    protected DropboxService dropbox;
    @MockitoBean
    protected JavaMailSender mail;
    @MockitoBean
    protected Clock clock;

    @BeforeEach
    void fixedTime() {
        when(clock.instant()).thenReturn(Instant.parse("2030-02-10T09:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    protected MockHttpServletResponse call(String method, String path, String token, Object body) throws Exception {
        var request = request(HttpMethod.valueOf(method), path);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    protected JsonNode json(int status, String method, String path, String token, Object body) throws Exception {
        var response = call(method, path, token, body);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        return mapper.readTree(response.getContentAsString());
    }

    protected Account account() throws Exception {
        String name = "u" + UUID.randomUUID().toString().replace("-", "");
        var registered = json(201, "POST", "/api/auth/register", null,
                Map.of("username", name, "password", "password123", "email", name + "@example.test",
                        "firstName", "Test", "lastName", "User"));
        String token = json(200, "POST", "/api/auth/login", null,
                Map.of("identifier", name, "password", "password123")).get("accessToken").asText();
        return new Account(registered.get("id").asLong(), name, token);
    }

    protected Map<String, Object> projectBody(List<Long> members) {
        return Map.of("name", "Course project", "description", "Simple REST API",
                "startDate", "2030-01-01", "endDate", "2030-12-31", "status", "INITIATED", "memberIds", members);
    }

    protected long project(Account owner, Account... members) throws Exception {
        return json(201, "POST", "/api/projects", owner.token(),
                projectBody(java.util.Arrays.stream(members).map(Account::id).toList())).get("id").asLong();
    }

    protected Map<String, Object> taskBody(long projectId, Long assigneeId, List<Long> labels) {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", "Course task");
        body.put("description", "Task description");
        body.put("projectId", projectId);
        body.put("assigneeId", assigneeId);
        body.put("priority", "MEDIUM");
        body.put("status", "NOT_STARTED");
        body.put("labelIds", labels);
        return body;
    }

    protected long task(Account actor, long project, Long assignee) throws Exception {
        return json(201, "POST", "/api/tasks", actor.token(), taskBody(project, assignee, List.of()))
                .get("id").asLong();
    }
}
