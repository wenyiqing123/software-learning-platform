package edu.wfust.learning.platform.auth;

import edu.wfust.learning.platform.entity.SysUserEntity;
import edu.wfust.learning.platform.mapper.SysUserMapper;
import edu.wfust.learning.platform.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenMapper refreshTokenMapper;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenMapper refreshTokenMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenMapper = refreshTokenMapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码 UTF-8 字节长度不能超过 72");
        }
        if (userMapper.findByUsername(request.username()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已被使用");
        }
        SysUserEntity user = new SysUserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank() ? request.username() : request.displayName().trim());
        user.setRole("STUDENT");
        user.setEnabled(true);
        userMapper.insert(user);
        return toResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        SysUserEntity user = userMapper.findByUsername(request.username());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确");
        }
        return toResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供有效的刷新令牌");
        RefreshTokenEntity existing = refreshTokenMapper.findActive(hash(rawToken));
        if (existing == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话已过期，请重新登录");
        existing.setRevokedAt(LocalDateTime.now());
        refreshTokenMapper.updateById(existing);
        SysUserEntity user = userMapper.selectById(existing.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或已停用");
        return toResponse(user);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        RefreshTokenEntity existing = refreshTokenMapper.findActive(hash(rawToken));
        if (existing != null) {
            existing.setRevokedAt(LocalDateTime.now());
            refreshTokenMapper.updateById(existing);
        }
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能为空或纯空白");
        }
        int codePoints = newPassword.codePointCount(0, newPassword.length());
        if (codePoints < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码长度不能少于 8 个字符");
        }
        byte[] utf8Bytes = newPassword.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码 UTF-8 字节长度不能超过 72");
        }
        SysUserEntity user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或已停用");
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        refreshTokenMapper.revokeAllActiveByUserId(userId, LocalDateTime.now());
    }

    public UserView currentUser(Long userId) {
        SysUserEntity user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或已停用");
        }
        return toView(user);
    }

    private AuthResponse toResponse(SysUserEntity user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshTokenEntity refresh = new RefreshTokenEntity();
        refresh.setUserId(user.getId());
        refresh.setTokenHash(hash(refreshToken));
        refresh.setExpiresAt(LocalDateTime.now().plusDays(30));
        refreshTokenMapper.insert(refresh);
        return new AuthResponse(jwtService.issue(user), refreshToken, "Bearer", jwtService.expirationSeconds(), 2_592_000, toView(user));
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) { throw new IllegalStateException("无法处理会话令牌", exception); }
    }

    private UserView toView(SysUserEntity user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public record RegisterRequest(String username, String password, String displayName) { }
    public record LoginRequest(String username, String password) { }
    public record UserView(Long id, String username, String displayName, String role) { }
    public record RefreshRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 512) String refreshToken) { }
    public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, long refreshExpiresIn, UserView user) { }
}
