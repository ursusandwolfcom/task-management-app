package com.example.taskmanagement.attachment.api;

/** ADMIN-only migration of local-disk attachments into Dropbox. */
public interface AttachmentBackfillOperations {
    record BackfillResult(int migrated, int skipped, int failed) {
    }

    BackfillResult backfillLocalToDropbox(long actorId, int limit);
}
