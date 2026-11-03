package com.example.taskmanagement.task.application;

import com.example.taskmanagement.planning.core.TaskSchedule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Soft leveling: overlapping planned [earlyStart, earlyFinish) for the same assignee.
 * Does not shift dates. Tasks without assignee are ignored.
 */
final class ResourceContention {
    private ResourceContention() {
    }

    static Set<Long> contendedTaskIds(List<TaskSchedule> tasks, Map<Long, Long> assigneeByTaskId) {
        Map<Long, List<TaskSchedule>> byAssignee = new HashMap<>();
        for (TaskSchedule task : tasks) {
            Long assignee = assigneeByTaskId.get(task.taskId());
            if (assignee == null || !task.earlyStart().isBefore(task.earlyFinish())) {
                continue;
            }
            byAssignee.computeIfAbsent(assignee, ignored -> new ArrayList<>()).add(task);
        }
        Set<Long> contended = new HashSet<>();
        for (List<TaskSchedule> group : byAssignee.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    if (overlaps(group.get(i), group.get(j))) {
                        contended.add(group.get(i).taskId());
                        contended.add(group.get(j).taskId());
                    }
                }
            }
        }
        return contended;
    }

    private static boolean overlaps(TaskSchedule left, TaskSchedule right) {
        Instant a0 = left.earlyStart();
        Instant a1 = left.earlyFinish();
        Instant b0 = right.earlyStart();
        Instant b1 = right.earlyFinish();
        return a0.isBefore(b1) && b0.isBefore(a1);
    }
}
