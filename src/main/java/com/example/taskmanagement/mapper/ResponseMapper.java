package com.example.taskmanagement.mapper;

import com.example.taskmanagement.dto.AttachmentResponse;
import com.example.taskmanagement.dto.CommentResponse;
import com.example.taskmanagement.dto.LabelRequest;
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
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** Compile-time DTO mappings; services retain authorization and persistence decisions. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ResponseMapper {
    UserResponse toResponse(User user);

    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "memberIds", source = "members")
    ProjectResponse toResponse(Project project);

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "assigneeId", source = "assignee.id")
    @Mapping(target = "labelIds", source = "labels")
    TaskResponse toResponse(Task task);

    @Mapping(target = "taskId", source = "task.id")
    @Mapping(target = "userId", source = "user.id")
    CommentResponse toResponse(Comment comment);

    @Mapping(target = "taskId", source = "task.id")
    AttachmentResponse toResponse(Attachment attachment);

    LabelResponse toResponse(Label label);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trim")
    Label toEntity(LabelRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trim")
    void update(LabelRequest request, @MappingTarget Label label);

    default Long userId(User user) {
        return user == null ? null : user.getId();
    }

    default Long labelId(Label label) {
        return label == null ? null : label.getId();
    }

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }
}
