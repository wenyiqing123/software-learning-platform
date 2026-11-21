package edu.wfust.learning.platform.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('STUDENT')")
public class QuizController {
    private final QuizService quizService;
    public QuizController(QuizService quizService) { this.quizService = quizService; }

    @GetMapping("/quizzes")
    public List<QuizService.QuestionView> questions(@RequestParam(required = false) Long knowledgePointId) {
        return quizService.questions(knowledgePointId);
    }

    @PostMapping("/quizzes/{questionId}/submit")
    public QuizService.SubmitAnswerResponse submit(Authentication authentication, @PathVariable Long questionId,
                                                    @Valid @RequestBody AnswerSubmission request) {
        return quizService.submit(Long.parseLong(authentication.getName()), questionId, request.answer());
    }

    @GetMapping("/wrong-book")
    public List<QuizService.WrongQuestionView> wrongBook(Authentication authentication) {
        return quizService.wrongQuestions(Long.parseLong(authentication.getName()));
    }

    @PostMapping("/wrong-book/{wrongQuestionId}/review")
    public QuizService.SubmitAnswerResponse review(Authentication authentication, @PathVariable Long wrongQuestionId,
                                                   @Valid @RequestBody AnswerSubmission request) {
        return quizService.review(Long.parseLong(authentication.getName()), wrongQuestionId, request.answer());
    }

    @PostMapping("/wrong-book/{wrongQuestionId}/mastered")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void markMastered(Authentication authentication, @PathVariable Long wrongQuestionId) {
        quizService.markMastered(Long.parseLong(authentication.getName()), wrongQuestionId);
    }

    public record AnswerSubmission(@NotEmpty List<String> answer) { }
}
