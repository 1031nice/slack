package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelMember;
import com.slack.domain.channel.ChannelRole;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceMember;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.exception.ChannelNotFoundException;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.ChannelRepository;
import com.slack.repository.WorkspaceMemberRepository;
import com.slack.repository.WorkspaceRepository;
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
@DisplayName("PermissionService 단위 테스트")
class PermissionServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @InjectMocks
    private PermissionService permissionService;

    private User owner;
    private User member;
    private User admin;
    private Workspace workspace;
    private Channel publicChannel;
    private Channel privateChannel;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser(1L, "owner");
        member = createUser(2L, "member");
        admin = createUser(3L, "admin");

        workspace = createWorkspace(1L, "Test Workspace", owner);
        publicChannel = createChannel(1L, "Public Channel", ChannelType.PUBLIC, workspace);
        privateChannel = createChannel(2L, "Private Channel", ChannelType.PRIVATE, workspace);
    }

    private User createUser(Long id, String name) throws Exception {
        User user = User.builder()
                .authUserId("auth-" + id)
                .email(name + "@example.com")
                .name(name)
                .build();
        setField(user, "id", id);
        return user;
    }

    private Workspace createWorkspace(Long id, String name, User owner) throws Exception {
        Workspace workspace = Workspace.builder()
                .name(name)
                .owner(owner)
                .build();
        setField(workspace, "id", id);
        return workspace;
    }

    private Channel createChannel(Long id, String name, ChannelType type, Workspace workspace) throws Exception {
        Channel channel = Channel.builder()
                .workspace(workspace)
                .name(name)
                .type(type)
                .createdBy(1L)
                .build();
        setField(channel, "id", id);
        return channel;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Workspace 소유자는 항상 true를 반환한다")
    void isWorkspaceOwner_Success() {
        // given
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        // when
        boolean result = permissionService.isWorkspaceOwner(1L, 1L);

        // then
        assertThat(result).isTrue();
        verify(workspaceRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Workspace 소유자가 아니면 false를 반환한다")
    void isWorkspaceOwner_False() {
        // given
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        // when
        boolean result = permissionService.isWorkspaceOwner(2L, 1L);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Workspace 멤버인지 확인할 수 있다")
    void isWorkspaceMember_Success() {
        // given
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(true);

        // when
        boolean result = permissionService.isWorkspaceMember(2L, 1L);

        // then
        assertThat(result).isTrue();
        verify(workspaceMemberRepository, times(1)).existsByWorkspaceIdAndUserId(1L, 2L);
    }

    @Test
    @DisplayName("Workspace 소유자는 ADMIN 역할 이상의 권한을 가진다")
    void hasWorkspaceRole_OwnerHasAllRoles() {
        // given
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        // when & then
        assertThat(permissionService.hasWorkspaceRole(1L, 1L, WorkspaceRole.MEMBER)).isTrue();
        assertThat(permissionService.hasWorkspaceRole(1L, 1L, WorkspaceRole.ADMIN)).isTrue();
        assertThat(permissionService.hasWorkspaceRole(1L, 1L, WorkspaceRole.OWNER)).isTrue();
    }

    @Test
    @DisplayName("Workspace ADMIN은 ADMIN과 MEMBER 권한을 가진다")
    void hasWorkspaceRole_AdminHasAdminAndMember() throws Exception {
        // given
        WorkspaceMember adminMember = WorkspaceMember.builder()
                .workspace(workspace)
                .user(admin)
                .role(WorkspaceRole.ADMIN)
                .build();
        setField(adminMember, "id", 1L);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 3L))
                .thenReturn(Optional.of(adminMember));

        // when & then
        assertThat(permissionService.hasWorkspaceRole(3L, 1L, WorkspaceRole.MEMBER)).isTrue();
        assertThat(permissionService.hasWorkspaceRole(3L, 1L, WorkspaceRole.ADMIN)).isTrue();
        assertThat(permissionService.hasWorkspaceRole(3L, 1L, WorkspaceRole.OWNER)).isFalse();
    }

    @Test
    @DisplayName("Workspace MEMBER는 MEMBER 권한만 가진다")
    void hasWorkspaceRole_MemberHasOnlyMember() throws Exception {
        // given
        WorkspaceMember memberMember = WorkspaceMember.builder()
                .workspace(workspace)
                .user(member)
                .role(WorkspaceRole.MEMBER)
                .build();
        setField(memberMember, "id", 1L);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(memberMember));

        // when & then
        assertThat(permissionService.hasWorkspaceRole(2L, 1L, WorkspaceRole.MEMBER)).isTrue();
        assertThat(permissionService.hasWorkspaceRole(2L, 1L, WorkspaceRole.ADMIN)).isFalse();
        assertThat(permissionService.hasWorkspaceRole(2L, 1L, WorkspaceRole.OWNER)).isFalse();
    }

    @Test
    @DisplayName("Workspace 멤버가 아니면 false를 반환한다")
    void hasWorkspaceRole_NotMember() {
        // given
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 999L))
                .thenReturn(Optional.empty());

        // when
        boolean result = permissionService.hasWorkspaceRole(999L, 1L, WorkspaceRole.MEMBER);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 Workspace를 조회하면 예외가 발생한다")
    void hasWorkspaceRole_WorkspaceNotFound() {
        // given
        when(workspaceRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> permissionService.hasWorkspaceRole(1L, 999L, WorkspaceRole.MEMBER))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    @DisplayName("Public Channel은 Workspace 멤버면 접근 가능하다")
    void canAccessChannel_PublicChannel() {
        // given
        when(channelRepository.findById(1L)).thenReturn(Optional.of(publicChannel));
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(true);

        // when
        boolean result = permissionService.canAccessChannel(2L, 1L);

        // then
        assertThat(result).isTrue();
        verify(channelRepository, times(1)).findById(1L);
        verify(workspaceMemberRepository, times(1)).existsByWorkspaceIdAndUserId(1L, 2L);
    }

    @Test
    @DisplayName("Private Channel은 Channel 멤버만 접근 가능하다")
    void canAccessChannel_PrivateChannel() {
        // given
        when(channelRepository.findById(2L)).thenReturn(Optional.of(privateChannel));
        when(channelMemberRepository.existsByChannelIdAndUserId(2L, 2L)).thenReturn(true);

        // when
        boolean result = permissionService.canAccessChannel(2L, 2L);

        // then
        assertThat(result).isTrue();
        verify(channelRepository, times(1)).findById(2L);
        verify(channelMemberRepository, times(1)).existsByChannelIdAndUserId(2L, 2L);
    }

    @Test
    @DisplayName("Private Channel의 멤버가 아니면 접근할 수 없다")
    void canAccessChannel_PrivateChannelNotMember() {
        // given
        when(channelRepository.findById(2L)).thenReturn(Optional.of(privateChannel));
        when(channelMemberRepository.existsByChannelIdAndUserId(2L, 999L)).thenReturn(false);

        // when
        boolean result = permissionService.canAccessChannel(999L, 2L);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Channel 멤버인지 확인할 수 있다")
    void isChannelMember_Success() {
        // given
        when(channelMemberRepository.existsByChannelIdAndUserId(1L, 2L)).thenReturn(true);

        // when
        boolean result = permissionService.isChannelMember(2L, 1L);

        // then
        assertThat(result).isTrue();
        verify(channelMemberRepository, times(1)).existsByChannelIdAndUserId(1L, 2L);
    }

    @Test
    @DisplayName("Channel ADMIN은 ADMIN과 MEMBER 권한을 가진다")
    void hasChannelRole_AdminHasAdminAndMember() throws Exception {
        // given
        ChannelMember adminMember = ChannelMember.builder()
                .channel(publicChannel)
                .user(admin)
                .role(ChannelRole.ADMIN)
                .build();
        setField(adminMember, "id", 1L);

        when(channelMemberRepository.findByChannelIdAndUserId(1L, 3L))
                .thenReturn(Optional.of(adminMember));

        // when & then
        assertThat(permissionService.hasChannelRole(3L, 1L, ChannelRole.MEMBER)).isTrue();
        assertThat(permissionService.hasChannelRole(3L, 1L, ChannelRole.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("Channel MEMBER는 MEMBER 권한만 가진다")
    void hasChannelRole_MemberHasOnlyMember() throws Exception {
        // given
        ChannelMember memberMember = ChannelMember.builder()
                .channel(publicChannel)
                .user(member)
                .role(ChannelRole.MEMBER)
                .build();
        setField(memberMember, "id", 1L);

        when(channelMemberRepository.findByChannelIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(memberMember));

        // when & then
        assertThat(permissionService.hasChannelRole(2L, 1L, ChannelRole.MEMBER)).isTrue();
        assertThat(permissionService.hasChannelRole(2L, 1L, ChannelRole.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 Channel을 조회하면 예외가 발생한다")
    void canAccessChannel_ChannelNotFound() {
        // given
        when(channelRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> permissionService.canAccessChannel(1L, 999L))
                .isInstanceOf(ChannelNotFoundException.class);
    }
}

