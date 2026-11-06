package com.example.taskmanagement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EmailService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender sender;
    private final String from;

    public EmailService(JavaMailSender sender, @Value("${notification.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Async
    @TransactionalEventListener
    public void send(TaskEmail event) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.recipient());
        message.setSubject(event.subject());
        message.setText(event.text());
        try {
            sender.send(message);
        } catch (MailException exception) {
            LOG.warn("Task email delivery failed; check SMTP configuration and availability");
        }
    }
}
