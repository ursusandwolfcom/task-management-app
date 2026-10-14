package com.example.taskmanagement.project.api;

/** Child modules protect project mutations under the existing project transaction lock. */
public interface ProjectContentGuard {
    void beforeRemoveMember(long projectId, long userId);

    void beforeDelete(long projectId);
}
