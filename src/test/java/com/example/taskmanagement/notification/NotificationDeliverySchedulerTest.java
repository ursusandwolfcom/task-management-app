package com.example.taskmanagement.notification;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.taskmanagement.integration.email.api.EmailOperations;
import com.example.taskmanagement.integration.telegram.api.TelegramOperations;
import com.example.taskmanagement.notification.api.NotificationOperations;
import com.example.taskmanagement.notification.application.NotificationDeliveryScheduler;
import org.junit.jupiter.api.Test;

class NotificationDeliverySchedulerTest {
    @Test
    void brokenChannelsDoNotPreventTheRemainingChannelFromRunning() {
        var notifications = mock(NotificationOperations.class);
        var email = mock(EmailOperations.class);
        var telegram = mock(TelegramOperations.class);
        doThrow(new IllegalStateException("Private failure detail")).when(notifications).projectPending(100);
        doThrow(new IllegalStateException("Private provider detail")).when(email).deliverPending(100);

        new NotificationDeliveryScheduler(notifications, email, telegram, 1000).dispatchPending();

        verify(notifications).projectPending(100);
        verify(email).deliverPending(100);
        verify(telegram).deliverPending(100);
    }
}
