package edu.wfust.learning.platform.admin;

import edu.wfust.learning.platform.entity.SysUserEntity;
import edu.wfust.learning.platform.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

/** Local onboarding only. Never promotes, enables, or resets an existing account. */
@Component
@Profile({"local", "docker"})
public class LocalAdminBootstrap implements CommandLineRunner {
    private final SysUserMapper users;
    private final PasswordEncoder passwords;
    private final Environment environment;
    public LocalAdminBootstrap(SysUserMapper users, PasswordEncoder passwords, Environment environment) {
        this.users = users; this.passwords = passwords; this.environment = environment;
    }
    @Override @Transactional
    public void run(String... args) {
        String username = environment.getProperty("SLP_BOOTSTRAP_ADMIN_USERNAME");
        String password = environment.getProperty("SLP_BOOTSTRAP_ADMIN_PASSWORD");
        if (username == null && password == null) return;
        if (username == null || !username.matches("[A-Za-z0-9_.-]{3,60}") || password == null
            || password.isBlank() || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("本地管理员引导需要 SLP_BOOTSTRAP_ADMIN_USERNAME 和 12 字符以上、72 字节以内的 SLP_BOOTSTRAP_ADMIN_PASSWORD");
        String displayName = environment.getProperty("SLP_BOOTSTRAP_ADMIN_DISPLAY_NAME", "课程管理员").trim();
        if (displayName.isEmpty() || displayName.length() > 80) throw new IllegalStateException("本地管理员显示名称长度必须为 1 到 80");
        var existing = users.findByUsername(username);
        if (existing != null) {
            if (!"ADMIN".equals(existing.getRole()) || !Boolean.TRUE.equals(existing.getEnabled()))
                throw new IllegalStateException("引导用户名已存在且不是可用管理员；不会提升权限或启用既有账户");
            return;
        }
        var user = new SysUserEntity(); user.setUsername(username); user.setDisplayName(displayName);
        user.setPasswordHash(passwords.encode(password)); user.setRole("ADMIN"); user.setEnabled(true);
        users.insert(user);
    }
}
