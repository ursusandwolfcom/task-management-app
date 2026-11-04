package com.example.taskmanagement.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.integration.email.application.EmailHtmlFormatter;
import com.example.taskmanagement.integration.email.application.EmailHtmlFormatter.Theme;
import org.junit.jupiter.api.Test;

class EmailHtmlFormatterTest {
    @Test
    void wrapsPlainTextWithBrandAndEscapesHtml() {
        String html = EmailHtmlFormatter.wrap("ProjectFlow", "Title <x>",
                "Line & one\nhttps://app.example.com/#/tasks/1");
        assertThat(html).contains("ProjectFlow").contains("Title &lt;x&gt;")
                .contains("Line &amp; one")
                .contains("href=\"https://app.example.com/#/tasks/1\"")
                .contains("<!DOCTYPE html>")
                .contains("#0f766e")
                .contains("#e8eef2");
    }

    @Test
    void slateThemeUsesDarkPalette() {
        String html = EmailHtmlFormatter.wrap("ProjectFlow", "Alert", "Body", Theme.SLATE);
        assertThat(html).contains("#0f172a").contains("#1e293b").contains("#38bdf8")
                .doesNotContain("#0f766e");
    }

    @Test
    void themeFromParsesKnownValues() {
        assertThat(Theme.from("default")).isEqualTo(Theme.DEFAULT);
        assertThat(Theme.from("SLATE")).isEqualTo(Theme.SLATE);
        assertThatThrownBy(() -> Theme.from("neon")).isInstanceOf(IllegalArgumentException.class);
    }
}
