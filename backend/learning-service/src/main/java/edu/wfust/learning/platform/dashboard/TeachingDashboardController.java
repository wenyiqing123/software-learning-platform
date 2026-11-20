package edu.wfust.learning.platform.dashboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/teaching-dashboard")
@Validated
public class TeachingDashboardController {
    private final TeachingDashboardService service;
    public TeachingDashboardController(TeachingDashboardService service){this.service=service;}
    @GetMapping public TeachingDashboardService.DashboardView dashboard(@RequestParam(required=false) @Min(1) @Max(30) Integer days,@RequestParam(required=false) @Positive Long courseId,Authentication authentication){return service.dashboard(days,courseId);}
}

