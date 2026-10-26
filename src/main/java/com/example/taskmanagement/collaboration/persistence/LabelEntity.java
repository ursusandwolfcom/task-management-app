package com.example.taskmanagement.collaboration.persistence;

import com.example.taskmanagement.collaboration.api.LabelData;
import com.example.taskmanagement.collaboration.api.LabelView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "labels", schema = "task_management")
public class LabelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false, updatable = false)
    private long projectId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "name_key", nullable = false, length = 300)
    private String nameKey;
    @Column(nullable = false, length = 7)
    private String color;
    @Version
    private long version;

    protected LabelEntity() {
    }

    public LabelEntity(long projectId, LabelData data) {
        this.projectId = projectId;
        replace(data);
    }

    public void replace(LabelData data) {
        name = data.name();
        nameKey = data.nameKey();
        color = data.color();
    }

    public LabelView view() {
        return new LabelView(id, projectId, name, color, version);
    }
}
