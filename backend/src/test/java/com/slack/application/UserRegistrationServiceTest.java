package com.slack.application;

import com.slack.domain.user.User;
import com.slack.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegistrationService Unit Tests")
class UserRegistrationServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .authUserId("auth-123")
                .email("test@example.com")
                .name("Test User")
                .build();
        setField(testUser, "id", 1L);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Should return existing user without creating new one")
    void findOrCreateUser_ExistingUser_ReturnsExistingUser() {
        when(userService.findByAuthUserIdOptional("auth-123")).thenReturn(Optional.of(testUser));

        User result = userRegistrationService.findOrCreateUser("auth-123", "test@example.com", "Test User");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAuthUserId()).isEqualTo("auth-123");
        assertThat(result.getEmail()).isEqualTo("test@example.com");

        verify(userService, times(1)).findByAuthUserIdOptional("auth-123");
        verify(userService, never()).createUser(any(), any(), any());
    }

    @Test
    @DisplayName("Should create new user when user does not exist")
    void findOrCreateUser_NewUser_CreatesAndReturnsNewUser() throws Exception {
        when(userService.findByAuthUserIdOptional("auth-456")).thenReturn(Optional.empty());

        User newUser = User.builder()
                .authUserId("auth-456")
                .email("new@example.com")
                .name("New User")
                .build();
        setField(newUser, "id", 2L);

        when(userService.createUser("auth-456", "new@example.com", "New User")).thenReturn(newUser);

        User result = userRegistrationService.findOrCreateUser("auth-456", "new@example.com", "New User");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getAuthUserId()).isEqualTo("auth-456");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getName()).isEqualTo("New User");

        verify(userService, times(1)).findByAuthUserIdOptional("auth-456");
        verify(userService, times(1)).createUser("auth-456", "new@example.com", "New User");
    }

    @Test
    @DisplayName("Should not auto-assign workspaces or channels to new users")
    void findOrCreateUser_NewUser_DoesNotCreateWorkspaceOrChannel() throws Exception {
        when(userService.findByAuthUserIdOptional("auth-789")).thenReturn(Optional.empty());

        User newUser = User.builder()
                .authUserId("auth-789")
                .email("newuser@example.com")
                .name("New User")
                .build();
        setField(newUser, "id", 3L);

        when(userService.createUser("auth-789", "newuser@example.com", "New User")).thenReturn(newUser);

        User result = userRegistrationService.findOrCreateUser("auth-789", "newuser@example.com", "New User");

        assertThat(result).isNotNull();
        assertThat(result.getAuthUserId()).isEqualTo("auth-789");

        verify(userService, times(1)).findByAuthUserIdOptional("auth-789");
        verify(userService, times(1)).createUser("auth-789", "newuser@example.com", "New User");
        verifyNoMoreInteractions(userService);
    }
}
