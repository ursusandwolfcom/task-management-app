package com.example.taskmanagement.collaboration.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabelRepository extends JpaRepository<LabelEntity, Long> {
    Page<LabelEntity> findByProjectId(long projectId, Pageable pageable);

    @Query("select l.projectId from LabelEntity l where l.id = :id")
    Optional<Long> projectId(@Param("id") long id);

    @Query("select count(l) > 0 from LabelEntity l where l.projectId = :projectId "
            + "and l.nameKey = :nameKey and l.id <> :excludedId")
    boolean nameExists(@Param("projectId") long projectId, @Param("nameKey") String nameKey,
            @Param("excludedId") long excludedId);
}
