package edu.wfust.learning.platform.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/reports")
@Validated
@PreAuthorize("hasRole('STUDENT')")
public class LearningReportController {
    private final LearningReportService service;
    public LearningReportController(LearningReportService service) { this.service=service; }
    @GetMapping("/learning")
    public LearningReportService.ReportView learning(Authentication auth, @RequestParam(defaultValue="7") @Min(1) @Max(30) int days, @RequestParam(required=false) @Positive Long courseId) {
        return service.report(Long.parseLong(auth.getName()), days, courseId);
    }
    @GetMapping("/learning/export")
    public ResponseEntity<byte[]> export(Authentication auth, @RequestParam(defaultValue="7") @Min(1) @Max(30) int days, @RequestParam(required=false) @Positive Long courseId) {
        var report=service.report(Long.parseLong(auth.getName()), days, courseId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("learning-report-"+report.from()+".csv", StandardCharsets.UTF_8).build().toString())
            .body(service.csv(report));
    }
}
