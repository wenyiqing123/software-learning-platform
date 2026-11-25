package edu.wfust.learning.platform.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空或纯空白");
        }
        int codePoints = rawPassword.codePointCount(0, rawPassword.length());
        if (codePoints < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码长度不能少于 8 个字符");
        }
        byte[] utf8Bytes = rawPassword.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码 UTF-8 字节长度不能超过 72");
        }
    }
}
