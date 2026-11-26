package edu.wfust.learning.platform.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCoverageAuditController {

    private final KnowledgeCoverageAuditService service;

    public AdminCoverageAuditController(KnowledgeCoverageAuditService service) {
        this.service = service;
    }

    @GetMapping("/coverage-audit")
    public KnowledgeCoverageAuditService.CoverageAuditReport coverageAudit(
            @RequestParam(required = false) Long courseId) {
        return service.audit(courseId);
    }
}
