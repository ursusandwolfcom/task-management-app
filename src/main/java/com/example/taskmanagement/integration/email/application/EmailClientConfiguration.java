package com.example.taskmanagement.integration.email.application;

import com.example.taskmanagement.integration.email.api.EmailException;
import com.example.taskmanagement.integration.email.api.EmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
public class EmailClientConfiguration {
    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.host:}") String host,
            @Value("${mail.from:}") String from) {
        String mailHost = host == null ? "" : host.strip();
        JavaMailSender javaMail = mailSender.getIfAvailable();
        if (mailHost.isEmpty() || javaMail == null) {
            return (to, subject, body) -> {
                throw new EmailException(EmailException.Reason.DISABLED);
            };
        }
        String fromAddress = from == null ? "" : from.strip();
        return (to, subject, body) -> {
            try {
                var message = new SimpleMailMessage();
                if (!fromAddress.isEmpty()) {
                    message.setFrom(fromAddress);
                }
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                javaMail.send(message);
            } catch (RuntimeException exception) {
                throw new EmailException(EmailException.Reason.DELIVERY_FAILED);
            }
        };
    }
}
