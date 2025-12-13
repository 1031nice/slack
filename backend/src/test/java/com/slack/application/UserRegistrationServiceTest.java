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
import org.springframework.dao.DataIntegrityViolationException;

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
    @DisplayName("Should return existing user without creating workspace/channel")
    void findOrCreateUser_ExistingUser() {
        when(userService.findByAuthUserIdOptional("auth-123")).thenReturn(Optional.of(testUser));

        User result = userRegistrationService.findOrCreateUser("auth-123", "test@example.com", "Test User");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(userService, times(1)).findByAuthUserIdOptional("auth-123");
        verify(userService, never()).createUser(any(), any(), any());
        verify(workspaceService, never()).findOrCreateDefaultWorkspace(any());
        verify(channelService, never()).findOrCreateDefaultChannel(any(), any());
    }

    @Test
    @DisplayName("Should create new user with default workspace and channel")
    void findOrCreateUser_NewUser() throws Exception {
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

        User result = userRegistrationService.findOrCreateUser("auth-456", "new@example.com", "New User");

        assertThat(result).isNotNull();
        assertThat(result.getAuthUserId()).isEqualTo("auth-456");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(userService, times(1)).findByAuthUserIdOptional("auth-456");
        verify(userService, times(1)).createUser("auth-456", "new@example.com", "New User");
    }

    @Test
    @DisplayName("Should create workspace and channel memberships for new user")
    void findOrCreateUser_NewUser_CreatesDefaultWorkspaceAndChannel() throws Exception {
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

        User result = userRegistrationService.findOrCreateUser("auth-789", "newuser@example.com", "New User");

        assertThat(result).isNotNull();
        assertThat(result.getAuthUserId()).isEqualTo("auth-789");

        verify(workspaceService, times(1)).findOrCreateDefaultWorkspace(newUser);
        verify(channelService, times(1)).findOrCreateDefaultChannel(defaultWorkspace, newUser.getId());
        verify(workspaceMemberRepository, times(1)).save(any(WorkspaceMember.class));
        verify(channelMemberRepository, times(1)).save(any(ChannelMember.class));
    }

    @Test
    @DisplayName("Should not create memberships for existing user")
    void findOrCreateUser_ExistingUser_DoesNotCreateDefaultWorkspaceAndChannel() {
        when(userService.findByAuthUserIdOptional("auth-123")).thenReturn(Optional.of(testUser));

        User result = userRegistrationService.findOrCreateUser("auth-123", "test@example.com", "Test User");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);

        verify(workspaceService, never()).findOrCreateDefaultWorkspace(any());
        verify(channelService, never()).findOrCreateDefaultChannel(any(), any());
        verify(workspaceMemberRepository, never()).save(any());
        verify(channelMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle duplicate workspace membership gracefully")
    void findOrCreateUser_DuplicateWorkspaceMembership_ShouldNotFail() throws Exception {
        when(userService.findByAuthUserIdOptional("auth-999")).thenReturn(Optional.empty());

        User newUser = User.builder()
                .authUserId("auth-999")
                .email("duplicate@example.com")
                .name("Duplicate User")
                .build();
        setField(newUser, "id", 4L);
        when(userService.createUser("auth-999", "duplicate@example.com", "Duplicate User")).thenReturn(newUser);

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

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        User result = userRegistrationService.findOrCreateUser("auth-999", "duplicate@example.com", "Duplicate User");

        assertThat(result).isNotNull();
        verify(workspaceMemberRepository, times(1)).save(any(WorkspaceMember.class));
        verify(channelMemberRepository, times(1)).save(any(ChannelMember.class));
    }

    @Test
    @DisplayName("Should handle duplicate channel membership gracefully")
    void findOrCreateUser_DuplicateChannelMembership_ShouldNotFail() throws Exception {
        when(userService.findByAuthUserIdOptional("auth-888")).thenReturn(Optional.empty());

        User newUser = User.builder()
                .authUserId("auth-888")
                .email("duplicate-channel@example.com")
                .name("Duplicate Channel User")
                .build();
        setField(newUser, "id", 5L);
        when(userService.createUser("auth-888", "duplicate-channel@example.com", "Duplicate Channel User"))
                .thenReturn(newUser);

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

        when(channelMemberRepository.save(any(ChannelMember.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        User result = userRegistrationService.findOrCreateUser("auth-888", "duplicate-channel@example.com",
                "Duplicate Channel User");

        assertThat(result).isNotNull();
        verify(workspaceMemberRepository, times(1)).save(any(WorkspaceMember.class));
        verify(channelMemberRepository, times(1)).save(any(ChannelMember.class));
    }
}
