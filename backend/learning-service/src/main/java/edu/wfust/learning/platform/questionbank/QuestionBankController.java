package edu.wfust.learning.platform.questionbank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/question-bank")
@Validated
public class QuestionBankController {
    private final QuestionBankService service;

    public QuestionBankController(QuestionBankService service) { this.service = service; }

    @GetMapping("/versions")
    public List<QuestionBankService.VersionView> versions(@RequestParam(required = false) @Positive Long courseId) {
        return service.listVersions(courseId);
    }

    @GetMapping("/versions/{id}/questions")
    public List<QuestionBankService.QuestionPreviewView> versionQuestions(@PathVariable Long id) {
        return service.listVersionQuestions(id);
    }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionBankService.VersionView createVersion(Authentication authentication,
                                                         @Valid @RequestBody QuestionBankService.CreateVersionRequest request) {
        return service.createVersion(userId(authentication), request);
    }

    @PostMapping("/versions/{id}/import")
    public QuestionBankService.ImportResult importQuestions(Authentication authentication, @PathVariable Long id,
                                                            @Valid @RequestBody QuestionBankService.ImportRequest request) {
        return service.importQuestions(id, userId(authentication), request);
    }

    @PutMapping("/versions/{id}/publish")
    public QuestionBankService.VersionView publish(@PathVariable Long id) {
        return service.publishVersion(id);
    }

    private long userId(Authentication authentication) { return Long.parseLong(authentication.getName()); }
}
