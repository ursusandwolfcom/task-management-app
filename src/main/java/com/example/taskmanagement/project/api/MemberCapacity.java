package com.example.taskmanagement.project.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** Effective daily working minutes (FTE) for a project member; default is the project calendar day. */
public record MemberCapacity(@Positive long userId, @Min(1) @Max(480) int dailyMinutes) {
}
