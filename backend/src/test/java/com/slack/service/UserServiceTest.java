package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelMember;
import com.slack.domain.channel.ChannelRole;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceMember;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.UserRepository;
import com.slack.repository.WorkspaceMemberRepository;
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

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private ChannelService channelService;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

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
    @DisplayName("authUserId로 User를 찾거나 없으면 생성한다 - 기존 User 반환")
    void findOrCreateByAuthUserId_ExistingUser() {
        // given
        when(userRepository.findByAuthUserId("auth-123")).thenReturn(Optional.of(testUser));

        // when
        User result = userService.findOrCreateByAuthUserId("auth-123", "test@example.com", "Test User");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(userRepository, times(1)).findByAuthUserId("auth-123");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("authUserId로 User를 찾거나 없으면 생성한다 - 새 User 생성")
    void findOrCreateByAuthUserId_NewUser() throws Exception {
        // given
        when(userRepository.findByAuthUserId("auth-456")).thenReturn(Optional.empty());
        User newUser = User.builder()
                .authUserId("auth-456")
                .email("new@example.com")
                .name("New User")
                .build();
        setField(newUser, "id", 2L);
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        Workspace defaultWorkspace = Workspace.builder()
                .name("Default Workspace")
                .owner(newUser)
                .build();
        setField(defaultWorkspace, "id", 1L);
        when(workspaceService.findOrCreateDefaultWorkspace(newUser)).thenReturn(defaultWorkspace);

        Channel defaultChannel = Channel.builder()
                .workspace(defaultWorkspace)
                .name("general")
                .type(ChannelType.PUBLIC)
                .createdBy(newUser.getId())
                .build();
        setField(defaultChannel, "id", 1L);
        when(channelService.findOrCreateDefaultChannel(defaultWorkspace, newUser.getId()))
                .thenReturn(defaultChannel);

        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(false);
        when(channelMemberRepository.existsByChannelIdAndUserId(1L, 2L)).thenReturn(false);

        // when
        User result = userService.findOrCreateByAuthUserId("auth-456", "new@example.com", "New User");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAuthUserId()).isEqualTo("auth-456");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(userRepository, times(1)).findByAuthUserId("auth-456");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("새 User 생성 시 기본 workspace와 channel이 자동으로 생성되고 멤버로 추가된다")
    void findOrCreateByAuthUserId_NewUser_CreatesDefaultWorkspaceAndChannel() throws Exception {
        // given
        when(userRepository.findByAuthUserId("auth-789")).thenReturn(Optional.empty());
        
        User newUser = User.builder()
                .authUserId("auth-789")
                .email("newuser@example.com")
                .name("New User")
                .build();
        setField(newUser, "id", 3L);
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        Workspace defaultWorkspace = Workspace.builder()
                .name("Default Workspace")
                .owner(newUser)
                .build();
        setField(defaultWorkspace, "id", 1L);
        when(workspaceService.findOrCreateDefaultWorkspace(newUser)).thenReturn(defaultWorkspace);

        Channel defaultChannel = Channel.builder()
                .workspace(defaultWorkspace)
                .name("general")
                .type(ChannelType.PUBLIC)
                .createdBy(newUser.getId())
                .build();
        setField(defaultChannel, "id", 1L);
        when(channelService.findOrCreateDefaultChannel(defaultWorkspace, newUser.getId()))
                .thenReturn(defaultChannel);

        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 3L)).thenReturn(false);
        when(channelMemberRepository.existsByChannelIdAndUserId(1L, 3L)).thenReturn(false);

        // when
        User result = userService.findOrCreateByAuthUserId("auth-789", "newuser@example.com", "New User");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAuthUserId()).isEqualTo("auth-789");
        
        // 기본 workspace와 channel 생성 확인
        verify(workspaceService, times(1)).findOrCreateDefaultWorkspace(newUser);
        verify(channelService, times(1)).findOrCreateDefaultChannel(defaultWorkspace, newUser.getId());
        
        // WorkspaceMember 생성 확인
        verify(workspaceMemberRepository, times(1)).existsByWorkspaceIdAndUserId(1L, 3L);
        verify(workspaceMemberRepository, times(1)).save(any(WorkspaceMember.class));
        
        // ChannelMember 생성 확인
        verify(channelMemberRepository, times(1)).existsByChannelIdAndUserId(1L, 3L);
        verify(channelMemberRepository, times(1)).save(any(ChannelMember.class));
    }

    @Test
    @DisplayName("기존 User는 기본 workspace/channel 생성 로직이 실행되지 않는다")
    void findOrCreateByAuthUserId_ExistingUser_DoesNotCreateDefaultWorkspaceAndChannel() {
        // given
        when(userRepository.findByAuthUserId("auth-123")).thenReturn(Optional.of(testUser));

        // when
        User result = userService.findOrCreateByAuthUserId("auth-123", "test@example.com", "Test User");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        
        // 기본 workspace/channel 생성이 호출되지 않음
        verify(workspaceService, never()).findOrCreateDefaultWorkspace(any());
        verify(channelService, never()).findOrCreateDefaultChannel(any(), any());
        verify(workspaceMemberRepository, never()).save(any());
        verify(channelMemberRepository, never()).save(any());
    }
}

