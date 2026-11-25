package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.KnowledgeServiceUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AiController.class)
public class AiExceptionHandler {
    public record ErrorResponse(String code, String message, boolean configured) { }

    @ExceptionHandler(AiFailure.class)
    ResponseEntity<ErrorResponse> failure(AiFailure failure) {
        return ResponseEntity.status(failure.status()).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(failure.code(), failure.getMessage(), failure.configured()));
    }
    @ExceptionHandler(KnowledgeServiceUnavailableException.class)
    ResponseEntity<ErrorResponse> knowledge(KnowledgeServiceUnavailableException exception) {
        return failure(AiFailure.knowledge(exception));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> invalid(Exception exception) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(new ErrorResponse("INVALID_AI_REQUEST", "问题或问答模式无效。", true));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse("FORBIDDEN", "当前账号无权执行该操作。", true));
    }
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ErrorResponse> unexpected(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse("AI_INTERNAL_ERROR", "AI 请求暂时无法处理，请稍后重试。", true));
    }
}
