package com.example.taskmanagement.repository;

import com.example.taskmanagement.model.Attachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    Page<Attachment> findByTaskId(Long taskId, Pageable pageable);
    boolean existsByTaskId(Long taskId);
}
