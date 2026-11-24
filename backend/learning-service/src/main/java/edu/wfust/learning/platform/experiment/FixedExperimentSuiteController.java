package edu.wfust.learning.platform.experiment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/experiments")
@PreAuthorize("hasRole('ADMIN')")
public class FixedExperimentSuiteController {

    @GetMapping("/fixed-suite")
    public FixedExperimentSuite.SuiteResponse getFixedSuite() {
        return FixedExperimentSuite.getSuite();
    }
}
