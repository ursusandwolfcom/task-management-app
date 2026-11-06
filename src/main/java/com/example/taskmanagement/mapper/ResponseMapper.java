package com.example.taskmanagement.mapper;

import com.example.taskmanagement.dto.AttachmentResponse;
import com.example.taskmanagement.dto.CommentResponse;
import com.example.taskmanagement.dto.LabelResponse;
import com.example.taskmanagement.dto.ProjectResponse;
import com.example.taskmanagement.dto.TaskResponse;
import com.example.taskmanagement.dto.UserResponse;
import com.example.taskmanagement.model.Attachment;
import com.example.taskmanagement.model.Comment;
import com.example.taskmanagement.model.Label;
import com.example.taskmanagement.model.Project;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.model.User;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapper {
    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getRole());
    }

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getStartDate(), project.getEndDate(), project.getStatus(), project.getOwner().getId(),
                project.getMembers().stream().map(User::getId).collect(Collectors.toSet()));
    }

    public TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getName(), task.getDescription(), task.getPriority(),
                task.getStatus(), task.getDueDate(), task.getProject().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getLabels().stream().map(Label::getId).collect(Collectors.toSet()));
    }

    public CommentResponse toResponse(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getTask().getId(), comment.getUser().getId(),
                comment.getText(), comment.getTimestamp());
    }

    public AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getTask().getId(),
                attachment.getDropboxFileId(), attachment.getFilename(), attachment.getUploadDate());
    }

    public LabelResponse toResponse(Label label) {
        return new LabelResponse(label.getId(), label.getName(), label.getColor());
    }
}
