package edu.wfust.learning.platform.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@Valid @RequestBody RegisterBody request) {
        return authService.register(new AuthService.RegisterRequest(request.username(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    public AuthService.AuthResponse login(@Valid @RequestBody LoginBody request) {
        return authService.login(new AuthService.LoginRequest(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public AuthService.AuthResponse refresh(@Valid @RequestBody AuthService.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) AuthService.RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordBody request) {
        Long userId = Long.parseLong(authentication.getName());
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
    }

    @GetMapping("/me")
    public AuthService.UserView current(Authentication authentication) {
        return authService.currentUser(Long.parseLong(authentication.getName()));
    }

    public record RegisterBody(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,60}") String username,
                               @NotBlank @Size(min = 8, max = 72) String password,
                               @Size(max = 80) String displayName) { }
    public record LoginBody(@NotBlank String username, @NotBlank String password) { }
    public record ChangePasswordBody(@NotBlank String currentPassword, @NotBlank String newPassword) { }
}
