package edu.wfust.learning.platform.config;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record ApiError(String code, String message, Instant timestamp) { }
    private ResponseEntity<ApiError> error(HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, Instant.now()));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex) {
        return error(ex.getStatusCode(), "REQUEST_REJECTED", ex.getReason() == null ? "请求未能完成" : ex.getReason());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ResponseEntity<ApiError> validation(Exception ex) { return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "输入格式不正确，请检查必填项与长度限制"); }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> argumentType(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "路径或查询参数格式不正确");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) { return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "该记录已存在或关联数据不符合要求"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ex) { return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前账号没有执行此操作的权限"); }
    @ExceptionHandler(RestClientException.class)
    ResponseEntity<ApiError> dependency(RestClientException ex) { return error(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_UNAVAILABLE", "知识图谱服务暂不可用，请确认现有 Python Bridge 与图数据库已启动"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Request failed", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时无法完成请求，请稍后重试");
    }
}
