package com.example.taskmanagement.integration.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.TelegramAccountOperations;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.integration.telegram.api.TelegramBotClient;
import com.example.taskmanagement.integration.telegram.api.TelegramOperations;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "telegram.bot-token=test-token",
        "telegram.bot-username=projectflow_test_bot",
        "telegram.webhook-secret=hook-secret",
        "telegram.app-public-base=https://app.example.com"
})
@Tag("integration")
class TelegramIntegrationTest {
    private static final String PASSWORD = "telegram-test-passphrase";

    @TestConfiguration
    static class TelegramTestBeans {
        @Bean
        @Primary
        TelegramBotClient recordingTelegramBotClient() {
            return new RecordingTelegramBotClient();
        }
    }

    @Autowired private TelegramOperations telegram;
    @Autowired private TelegramAccountOperations accounts;
    @Autowired private TelegramBotClient botClient;
    @Autowired private TaskOperations tasks;
    @Autowired private ProjectOperations projects;
    @Autowired private IdentityOperations identity;
    @Autowired private TokenService tokens;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private UserView owner;
    private UserView member;
    private ProjectView project;
    private TaskView task;
    private RecordingTelegramBotClient bot;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        bot = (RecordingTelegramBotClient) botClient;
        bot.clear();
        owner = register("tg_owner");
        member = register("tg_member");
        project = projects.create(owner.id(), new ProjectData("Project", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        task = tasks.create(owner.id(), project.id(),
                new TaskData("Assigned", "", TaskPriority.HIGH, 15, null, member.id(), TaskBucket.WORK));
    }

    @Test
    void linkViaWebhookAndDeliverAssignment() throws Exception {
        var link = telegram.createLink(member.id());
        assertThat(link.deepLink()).contains("projectflow_test_bot");

        mvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Bot-Api-Secret-Token", "hook-secret")
                        .content("""
                                {"message":{"chat":{"id":4242},"text":"/start %s"}}
                                """.formatted(link.token())))
                .andExpect(status().isOk());

        assertThat(accounts.status(member.id()).linked()).isTrue();
        assertThat(bot.sent()).anyMatch(item -> item.chatId() == 4242L && item.text().contains("привязан"));

        bot.clear();
        assertThat(telegram.deliverPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(bot.sent()).anyMatch(item -> item.chatId() == 4242L
                && item.text().contains("Назначена задача")
                && item.text().contains("https://app.example.com/#/tasks/"));

        mvc.perform(get("/api/telegram").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void muteSkipsDeliveryAndCompleteNotifiesOwner() {
        accounts.bindChat(accounts.createLinkTicket(member.id()).token(), 1001L);
        accounts.bindChat(accounts.createLinkTicket(owner.id()).token(), 1002L);
        accounts.setEnabled(member.id(), false);
        bot.clear();

        telegram.deliverPending(20);
        assertThat(bot.sent()).noneMatch(item -> item.chatId() == 1001L);

        task = tasks.execute(owner.id(), task.id(), Command.START, task.version());
        telegram.deliverPending(20);
        bot.clear();
        task = tasks.execute(member.id(), task.id(), Command.COMPLETE, task.version());
        assertThat(telegram.deliverPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(bot.sent()).anyMatch(item -> item.chatId() == 1002L && item.text().contains("завершена"));
        assertThat(bot.sent()).noneMatch(item -> item.chatId() == 1001L);
    }

    @Test
    void webhookRejectsBadSecretAndApiUnlinkWorks() throws Exception {
        var link = telegram.createLink(member.id());
        mvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Bot-Api-Secret-Token", "wrong")
                        .content("""
                                {"message":{"chat":{"id":7},"text":"/start %s"}}
                                """.formatted(link.token())))
                .andExpect(status().isForbidden());

        accounts.bindChat(link.token(), 7L);
        mvc.perform(put("/api/telegram/enabled").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(delete("/api/telegram/link").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false));
    }

    private UserView register(String name) {
        return identity.register(new RegisterUser(name, name + "@example.com", PASSWORD, "T", "G"));
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
    }
}
