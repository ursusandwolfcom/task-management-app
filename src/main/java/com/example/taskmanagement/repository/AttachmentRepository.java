package com.example.taskmanagement.repository;

import com.example.taskmanagement.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    Page<Attachment> findByTaskIdOrderByIdDesc(Long taskId, Pageable pageable);
    boolean existsByTaskId(Long taskId);
}
