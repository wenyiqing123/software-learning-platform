package edu.wfust.learning.platform.admin;

import edu.wfust.learning.platform.entity.SysUserEntity;
import edu.wfust.learning.platform.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalAdminBootstrapTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final MockEnvironment environment = new MockEnvironment();
    private LocalAdminBootstrap bootstrap() { return new LocalAdminBootstrap(users, encoder, environment); }
    @Test void bootstrapIsEnabledOnlyForLocalAndDockerProfiles() {
        assertThat(LocalAdminBootstrap.class.getAnnotation(Profile.class).value())
                .containsExactly("local", "docker");
    }
    @Test void absentEnvironmentNeverCreatesAnAccount() {
        bootstrap().run(); verifyNoInteractions(users);
    }
    @Test void incompleteOrWeakCredentialsFailWithoutCreatingAnAccount() {
        environment.setProperty("SLP_BOOTSTRAP_ADMIN_USERNAME", "local-admin");
        assertThatThrownBy(() -> bootstrap().run()).isInstanceOf(IllegalStateException.class);
        environment.setProperty("SLP_BOOTSTRAP_ADMIN_PASSWORD", "short");
        assertThatThrownBy(() -> bootstrap().run()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users);
    }
    @Test void newAccountUsesOnlyProvidedCredentialsAndBcrypt() {
        credentials(); bootstrap().run();
        var saved = ArgumentCaptor.forClass(SysUserEntity.class); verify(users).insert(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("local-admin");
        assertThat(saved.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(saved.getValue().getEnabled()).isTrue();
        assertThat(encoder.matches("test-only-bootstrap-credential", saved.getValue().getPasswordHash())).isTrue();
    }
    @Test void existingStudentIsNeverPromoted() {
        credentials(); var student = new SysUserEntity(); student.setRole("STUDENT"); student.setEnabled(true);
        when(users.findByUsername("local-admin")).thenReturn(student);
        assertThatThrownBy(() -> bootstrap().run()).isInstanceOf(IllegalStateException.class);
        assertThat(student.getRole()).isEqualTo("STUDENT");
        verify(users, never()).insert(any(SysUserEntity.class)); verify(users, never()).updateById(any(SysUserEntity.class));
    }
    @Test void existingAdminIsNotResetAndDisabledAdminIsNotReenabled() {
        credentials(); var admin = new SysUserEntity(); admin.setRole("ADMIN"); admin.setEnabled(true); admin.setPasswordHash("existing-hash");
        when(users.findByUsername("local-admin")).thenReturn(admin);
        bootstrap().run(); assertThat(admin.getPasswordHash()).isEqualTo("existing-hash");
        admin.setEnabled(false);
        assertThatThrownBy(() -> bootstrap().run()).isInstanceOf(IllegalStateException.class);
        assertThat(admin.getEnabled()).isFalse(); verify(users, never()).updateById(any(SysUserEntity.class));
    }
    private void credentials() {
        environment.setProperty("SLP_BOOTSTRAP_ADMIN_USERNAME", "local-admin");
        environment.setProperty("SLP_BOOTSTRAP_ADMIN_PASSWORD", "test-only-bootstrap-credential");
    }
}
