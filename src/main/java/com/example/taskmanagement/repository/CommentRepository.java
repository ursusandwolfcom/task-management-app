package com.example.taskmanagement.repository;

import com.example.taskmanagement.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByTaskIdOrderByIdDesc(Long taskId, Pageable pageable);
    void deleteByTaskId(Long taskId);
}
