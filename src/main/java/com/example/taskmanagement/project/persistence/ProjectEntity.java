package com.example.taskmanagement.project.persistence;

import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectStatus;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.shared.persistence.FiniteDateJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.JdbcType;

@Entity
@Table(name = "projects", schema = "task_management")
public class ProjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 10000)
    private String description;
    @Column(name = "start_date", nullable = false)
    // Direct JDBC LocalDate mapping preserves proleptic years, including BC and year zero.
    @JdbcType(FiniteDateJdbcType.class)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    @JdbcType(FiniteDateJdbcType.class)
    private LocalDate endDate;
    @Column(nullable = false, length = 64)
    private String timezone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.INITIATED;
    @Column(name = "owner_id", nullable = false)
    private long ownerId;
    @Version
    private long version;
    @Column(name = "input_revision", nullable = false)
    private long inputRevision;
    @Column(name = "calendar_revision", nullable = false)
    private long calendarRevision;

    protected ProjectEntity() {
    }

    public ProjectEntity(ProjectData data, long ownerId) {
        name = data.name();
        description = data.description();
        startDate = data.startDate();
        endDate = data.endDate();
        timezone = data.timezone();
        this.ownerId = ownerId;
    }

    public ProjectView view() {
        return new ProjectView(id, name, description, startDate, endDate, timezone, status,
                ownerId, version, inputRevision, calendarRevision);
    }

    public void update(ProjectData data) {
        if (!Objects.equals(startDate, data.startDate()) || !Objects.equals(endDate, data.endDate())
                || !Objects.equals(timezone, data.timezone())) {
            calendarRevision = Math.addExact(calendarRevision, 1);
        }
        name = data.name();
        description = data.description();
        startDate = data.startDate();
        endDate = data.endDate();
        timezone = data.timezone();
        inputChanged();
    }

    public void transferOwner(long userId) {
        if (ownerId != userId) {
            ownerId = userId;
            inputChanged();
        }
    }

    public void inputChanged() {
        inputRevision = Math.addExact(inputRevision, 1);
    }
}
