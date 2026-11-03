package com.example.taskmanagement.integration.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.integration.email.application.EmailHtmlFormatter;
import org.junit.jupiter.api.Test;

class EmailHtmlFormatterTest {
    @Test
    void wrapsPlainTextWithBrandAndEscapesHtml() {
        String html = EmailHtmlFormatter.wrap("ProjectFlow", "Title <x>",
                "Line & one\nhttps://app.example.com/#/tasks/1");
        assertThat(html).contains("ProjectFlow").contains("Title &lt;x&gt;")
                .contains("Line &amp; one")
                .contains("href=\"https://app.example.com/#/tasks/1\"")
                .contains("<!DOCTYPE html>");
    }
}
