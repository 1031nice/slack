package com.slack.application;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelMember;
import com.slack.domain.channel.ChannelRole;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceMember;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.WorkspaceMemberRepository;
import com.slack.service.ChannelService;
import com.slack.service.UserService;
import com.slack.service.WorkspaceService;
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
@DisplayName("UserRegistrationService 단위 테스트")
class UserRegistrationServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private ChannelService channelService;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

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
    @DisplayName("authUserId로 User를 찾거나 없으면 생성한다 - 기존 User 반환")
    void findOrCreateUser_ExistingUser() {
        // given
        when(userService.findByAuthUserIdOptional("auth-123")).thenReturn(Optional.of(testUser));

        // when
        User result = userRegistrationService.findOrCreateUser("auth-123", "test@example.com", "Test User");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(userService, times(1)).findByAuthUserIdOptional("auth-123");
        verify(userService, never()).createUser(any(), any(), any());
        verify(workspaceService, never()).findOrCreateDefaultWorkspace(any());
        verify(channelService, never()).findOrCreateDefaultChannel(any(), any());
    }

    @Test
    @DisplayName("authUserId로 User를 찾거나 없으면 생성한다 - 새 User 생성")
    void findOrCreateUser_NewUser() throws Exception {
        // given
        when(userService.findByAuthUserIdOptional("auth-456")).thenReturn(Optional.empty());
        User newUser = User.builder()
                .authUserId("auth-456")
                .email("new@example.com")
                .name("New User")
                .build();
        setField(newUser, "id", 2L);
        when(userService.createUser("auth-456", "new@example.com", "New User")).thenReturn(newUser);

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
        User result = userRegistrationService.findOrCreateUser("auth-456", "new@example.com", "New User");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAuthUserId()).isEqualTo("auth-456");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(userService, times(1)).findByAuthUserIdOptional("auth-456");
        verify(userService, times(1)).createUser("auth-456", "new@example.com", "New User");
    }

    @Test
    @DisplayName("새 User 생성 시 기본 workspace와 channel이 자동으로 생성되고 멤버로 추가된다")
    void findOrCreateUser_NewUser_CreatesDefaultWorkspaceAndChannel() throws Exception {
        // given
        when(userService.findByAuthUserIdOptional("auth-789")).thenReturn(Optional.empty());
        
        User newUser = User.builder()
                .authUserId("auth-789")
                .email("newuser@example.com")
                .name("New User")
                .build();
        setField(newUser, "id", 3L);
        when(userService.createUser("auth-789", "newuser@example.com", "New User")).thenReturn(newUser);

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
        User result = userRegistrationService.findOrCreateUser("auth-789", "newuser@example.com", "New User");

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
    void findOrCreateUser_ExistingUser_DoesNotCreateDefaultWorkspaceAndChannel() {
        // given
        when(userService.findByAuthUserIdOptional("auth-123")).thenReturn(Optional.of(testUser));

        // when
        User result = userRegistrationService.findOrCreateUser("auth-123", "test@example.com", "Test User");

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

