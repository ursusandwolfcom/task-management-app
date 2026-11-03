package com.example.taskmanagement.activity.api;

/** Action type constants written into task activity history. */
public final class ActivityEvents {
    public static final String TASK_CREATED = "task.created";
    public static final String TASK_UPDATED = "task.updated";
    public static final String TASK_STARTED = "task.started";
    public static final String TASK_COMPLETED = "task.completed";
    public static final String TASK_FAILED = "task.failed";
    public static final String TASK_CANCELLED = "task.cancelled";
    public static final String TASK_RETRIED = "task.retried";
    public static final String COMMENT_CREATED = "comment.created";
    public static final String COMMENT_DELETED = "comment.deleted";
    public static final String LABEL_LINKED = "label.linked";
    public static final String LABEL_UNLINKED = "label.unlinked";
    public static final String ATTACHMENT_UPLOADED = "attachment.uploaded";
    public static final String ATTACHMENT_DELETED = "attachment.deleted";

    private ActivityEvents() {
    }
}
