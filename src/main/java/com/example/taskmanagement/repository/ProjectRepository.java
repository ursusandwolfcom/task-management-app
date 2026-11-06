package com.example.taskmanagement.repository;

import com.example.taskmanagement.model.Project;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findForUpdate(@Param("id") Long id);

    @Query("select p from Project p where p.owner.id = :userId or :userId in "
            + "(select m.id from p.members m) order by p.id desc")
    Page<Project> findAccessible(@Param("userId") Long userId, Pageable pageable);
}
