package com.example.taskmanagement.project.persistence;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectHolidayRepository {
    private final JdbcTemplate jdbc;

    public ProjectHolidayRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<LocalDate> list(long projectId) {
        List<LocalDate> dates = jdbc.query(
                "select holiday_date from task_management.project_holidays where project_id = ? order by holiday_date",
                (rs, rowNum) -> rs.getObject(1, LocalDate.class), projectId);
        return Set.copyOf(dates);
    }

    public void replace(long projectId, Set<LocalDate> holidays) {
        jdbc.update("delete from task_management.project_holidays where project_id = ?", projectId);
        for (LocalDate day : new HashSet<>(holidays)) {
            jdbc.update("insert into task_management.project_holidays(project_id, holiday_date) values (?, ?)",
                    projectId, day);
        }
    }
}
