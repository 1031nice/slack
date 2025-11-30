package com.slack.service;

import com.slack.domain.user.User;
import com.slack.repository.UserRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

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
    @DisplayName("authUserId로 User를 찾을 수 있다")
    void findByAuthUserId_Success() {
        // given
        when(userRepository.findByAuthUserId("auth-123")).thenReturn(Optional.of(testUser));

        // when
        User result = userService.findByAuthUserId("auth-123");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAuthUserId()).isEqualTo("auth-123");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(userRepository, times(1)).findByAuthUserId("auth-123");
    }

    @Test
    @DisplayName("authUserId로 User를 찾지 못하면 예외가 발생한다")
    void findByAuthUserId_NotFound() {
        // given
        when(userRepository.findByAuthUserId("auth-999")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.findByAuthUserId("auth-999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found with authUserId: auth-999");
    }

    @Test
    @DisplayName("ID로 User를 찾을 수 있다")
    void findById_Success() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // when
        User result = userService.findById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("ID로 User를 찾지 못하면 예외가 발생한다")
    void findById_NotFound() {
        // given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found with id: 999");
    }

    @Test
    @DisplayName("authUserId로 User를 Optional로 찾을 수 있다")
    void findByAuthUserIdOptional_Success() {
        // given
        when(userRepository.findByAuthUserId("auth-123")).thenReturn(Optional.of(testUser));

        // when
        Optional<User> result = userService.findByAuthUserIdOptional("auth-123");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getAuthUserId()).isEqualTo("auth-123");
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        verify(userRepository, times(1)).findByAuthUserId("auth-123");
    }

    @Test
    @DisplayName("authUserId로 User를 Optional로 찾지 못하면 empty를 반환한다")
    void findByAuthUserIdOptional_NotFound() {
        // given
        when(userRepository.findByAuthUserId("auth-999")).thenReturn(Optional.empty());

        // when
        Optional<User> result = userService.findByAuthUserIdOptional("auth-999");

        // then
        assertThat(result).isEmpty();
        verify(userRepository, times(1)).findByAuthUserId("auth-999");
    }

    @Test
    @DisplayName("새 User를 생성할 수 있다")
    void createUser_Success() throws Exception {
        // given
        User newUser = User.builder()
                .authUserId("auth-456")
                .email("new@example.com")
                .name("New User")
                .build();
        setField(newUser, "id", 2L);
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // when
        User result = userService.createUser("auth-456", "new@example.com", "New User");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAuthUserId()).isEqualTo("auth-456");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getName()).isEqualTo("New User");
        verify(userRepository, times(1)).save(any(User.class));
    }
}

