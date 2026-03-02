package com.gestormatic.backend.auth;

import com.gestormatic.backend.auth.config.SupabaseProperties;
import com.gestormatic.backend.auth.dto.SignUpRequest;
import com.gestormatic.backend.auth.model.User;
import com.gestormatic.backend.auth.repo.UserRepository;
import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceSignUpTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        SupabaseProperties props = new SupabaseProperties();
        props.setUrl("https://test.example.supabase.co");
        props.setServiceRoleKey("test-service-key");
        authService = new AuthService(userRepository, jdbcTemplate, props);
    }

    // ─── signUp: required field validation ───────────────────────────────────

    @Test
    void signUpThrowsWhenEmailIsNull() {
        assertThatThrownBy(() -> authService.signUp(request(null, "pass", "Carlos", "default")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email is required");
    }

    @Test
    void signUpThrowsWhenEmailIsBlank() {
        assertThatThrownBy(() -> authService.signUp(request("  ", "pass", "Carlos", "default")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email is required");
    }

    @Test
    void signUpThrowsWhenPasswordIsNull() {
        assertThatThrownBy(() -> authService.signUp(request("user@test.com", null, "Carlos", "default")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("password is required");
    }

    @Test
    void signUpThrowsWhenPasswordIsBlank() {
        assertThatThrownBy(() -> authService.signUp(request("user@test.com", "  ", "Carlos", "default")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("password is required");
    }

    @Test
    void signUpThrowsWhenDisplayNameIsNull() {
        assertThatThrownBy(() -> authService.signUp(request("user@test.com", "pass", null, "default")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("displayName is required");
    }

    @Test
    void signUpThrowsWhenDisplayNameIsBlank() {
        assertThatThrownBy(() -> authService.signUp(request("user@test.com", "pass", "  ", "default")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("displayName is required");
    }

    @Test
    void signUpThrowsWhenTenantIdIsNull() {
        assertThatThrownBy(() -> authService.signUp(request("user@test.com", "pass", "Carlos", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId is required");
    }

    @Test
    void signUpThrowsWhenTenantIdIsBlank() {
        assertThatThrownBy(() -> authService.signUp(request("user@test.com", "pass", "Carlos", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId is required");
    }

    // ─── syncIfChanged (via getProfile) ──────────────────────────────────────

    @Test
    void getProfileSyncsEmailWhenJwtDiffers() {
        User user = savedUser("old@example.com", "Carlos");
        when(userRepository.findByTenantIdAndAuthUid("acme", "uid-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SupabasePrincipal principal = new SupabasePrincipal("uid-1", "new@example.com", "Carlos", "acme", List.of());
        authService.getProfile("acme", principal, List.of());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Carlos");
    }

    @Test
    void getProfileSyncsDisplayNameWhenJwtDiffers() {
        User user = savedUser("user@example.com", "Old Name");
        when(userRepository.findByTenantIdAndAuthUid("acme", "uid-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SupabasePrincipal principal = new SupabasePrincipal("uid-1", "user@example.com", "New Name", "acme", List.of());
        authService.getProfile("acme", principal, List.of());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayName()).isEqualTo("New Name");
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void getProfileUpdatesUpdatedAtWhenSyncing() {
        OffsetDateTime before = OffsetDateTime.now().minusMinutes(5);
        User user = savedUser("old@example.com", "Carlos");
        user.setUpdatedAt(before);
        when(userRepository.findByTenantIdAndAuthUid("acme", "uid-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SupabasePrincipal principal = new SupabasePrincipal("uid-1", "new@example.com", "Carlos", "acme", List.of());
        authService.getProfile("acme", principal, List.of());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUpdatedAt()).isAfter(before);
    }

    @Test
    void getProfileDoesNotSaveWhenValuesAreUnchanged() {
        User user = savedUser("user@example.com", "Carlos");
        when(userRepository.findByTenantIdAndAuthUid("acme", "uid-1")).thenReturn(Optional.of(user));

        SupabasePrincipal principal = new SupabasePrincipal("uid-1", "user@example.com", "Carlos", "acme", List.of());
        authService.getProfile("acme", principal, List.of());

        verify(userRepository, never()).save(any());
    }

    @Test
    void getProfileDoesNotSaveWhenJwtNameIsNull() {
        User user = savedUser("user@example.com", "Carlos");
        when(userRepository.findByTenantIdAndAuthUid("acme", "uid-1")).thenReturn(Optional.of(user));

        SupabasePrincipal principal = new SupabasePrincipal("uid-1", "user@example.com", null, "acme", List.of());
        authService.getProfile("acme", principal, List.of());

        verify(userRepository, never()).save(any());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private SignUpRequest request(String email, String password, String displayName, String tenantId) {
        SignUpRequest req = new SignUpRequest();
        req.setEmail(email);
        req.setPassword(password);
        req.setDisplayName(displayName);
        req.setTenantId(tenantId);
        return req;
    }

    private User savedUser(String email, String displayName) {
        User user = new User();
        user.setId(1L);
        user.setTenantId("acme");
        user.setAuthUid("uid-1");
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }
}
