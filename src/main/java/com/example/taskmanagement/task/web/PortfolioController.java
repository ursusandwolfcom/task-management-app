package com.example.taskmanagement.task.web;

import com.example.taskmanagement.task.api.PortfolioOperations;
import com.example.taskmanagement.task.api.PortfolioOperations.PortfolioPage;
import com.example.taskmanagement.task.api.PortfolioOperations.WorkloadSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {
    private final PortfolioOperations portfolio;

    public PortfolioController(PortfolioOperations portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping
    @Operation(summary = "Portfolio risk overview for accessible projects",
            description = "ON_TRACK / AT_RISK (task due overrun) / OVERDUE (project end overrun). "
                    + "Uses working-time CPM; GET never mutates data. Cache-Control: no-store.")
    public ResponseEntity<PortfolioPage> overview(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @PositiveOrZero @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(portfolio.overview(Long.parseLong(jwt.getSubject()), page, size));
    }

    @GetMapping("/workload")
    @Operation(summary = "Assignee open-work load across accessible projects",
            description = "Counts NOT_STARTED/IN_PROGRESS/FAILED assigned tasks, summed estimates, and "
                    + "calendar overdue by dueDate. No leveling; GET never mutates data.")
    public ResponseEntity<WorkloadSnapshot> workload(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(portfolio.workload(Long.parseLong(jwt.getSubject())));
    }
}
