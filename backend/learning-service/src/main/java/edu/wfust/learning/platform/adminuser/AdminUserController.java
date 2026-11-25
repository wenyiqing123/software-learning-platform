package edu.wfust.learning.platform.adminuser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public AdminUserService.UserPageView listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.listUsers(keyword, role, enabled, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserService.UserSummaryView createUser(@Valid @RequestBody CreateUserBody body) {
        return service.createUser(new AdminUserService.CreateUserRequest(
                body.username(), body.password(), body.displayName(), body.role(), body.enabled()));
    }

    @PutMapping("/{id}")
    public AdminUserService.UserSummaryView updateUser(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserBody body) {
        Long operatorId = Long.parseLong(authentication.getName());
        return service.updateUser(operatorId, id, new AdminUserService.UpdateUserRequest(body.displayName(), body.role()));
    }

    @PutMapping("/{id}/enabled")
    public AdminUserService.UserSummaryView setUserEnabled(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody EnabledBody body) {
        Long operatorId = Long.parseLong(authentication.getName());
        return service.setUserEnabled(operatorId, id, body.enabled());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordBody body) {
        service.resetPassword(id, body.newPassword());
    }

    @PostMapping("/{id}/revoke-tokens")
    public Map<String, Integer> revokeTokens(Authentication authentication, @PathVariable Long id) {
        Long operatorId = Long.parseLong(authentication.getName());
        int revoked = service.revokeTokens(operatorId, id);
        return Map.of("revokedCount", revoked);
    }

    @GetMapping("/{id}/profile")
    public AdminUserService.UserProfileView userProfile(@PathVariable Long id) {
        return service.userProfile(id);
    }

    public record CreateUserBody(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,60}") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 80) String displayName,
            @NotBlank String role,
            Boolean enabled) { }

    public record UpdateUserBody(
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank String role) { }

    public record EnabledBody(@NotNull Boolean enabled) { }

    public record ResetPasswordBody(@NotBlank String newPassword) { }
}
