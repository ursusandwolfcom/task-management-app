package com.example.taskmanagement.integration.email.application;

import com.example.taskmanagement.integration.email.api.EmailException;
import com.example.taskmanagement.integration.email.api.EmailSender;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Configuration(proxyBeanMethods = false)
public class EmailClientConfiguration {
    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.host:}") String host,
            @Value("${mail.from:}") String from) {
        String mailHost = host == null ? "" : host.strip();
        JavaMailSender javaMail = mailSender.getIfAvailable();
        if (mailHost.isEmpty() || javaMail == null) {
            return (to, subject, textBody, htmlBody) -> {
                throw new EmailException(EmailException.Reason.DISABLED);
            };
        }
        String fromAddress = from == null ? "" : from.strip();
        return (to, subject, textBody, htmlBody) -> {
            try {
                MimeMessage message = javaMail.createMimeMessage();
                var helper = new MimeMessageHelper(message, true, "UTF-8");
                if (!fromAddress.isEmpty()) {
                    helper.setFrom(fromAddress);
                }
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(textBody == null ? "" : textBody, htmlBody == null ? "" : htmlBody);
                javaMail.send(message);
            } catch (Exception exception) {
                throw new EmailException(EmailException.Reason.DELIVERY_FAILED);
            }
        };
    }
}
