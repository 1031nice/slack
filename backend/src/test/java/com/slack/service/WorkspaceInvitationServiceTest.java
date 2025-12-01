package com.slack.service;

import com.slack.domain.user.User;
import com.slack.domain.workspace.InvitationStatus;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceInvitation;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.dto.workspace.AcceptInvitationRequest;
import com.slack.dto.workspace.WorkspaceInviteRequest;
import com.slack.exception.InvitationAlreadyAcceptedException;
import com.slack.exception.InvitationExpiredException;
import com.slack.exception.InvitationNotFoundException;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.repository.WorkspaceInvitationRepository;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceInvitationService 단위 테스트")
class WorkspaceInvitationServiceTest {

    @Mock
    private WorkspaceInvitationRepository invitationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private WorkspaceInvitationService invitationService;

    private Workspace workspace;
    private User inviter;
    private User invitee;
    private WorkspaceInviteRequest inviteRequest;

    @BeforeEach
    void setUp() throws Exception {
        inviter = createUser(1L, "auth-user-1", "inviter@example.com", "Inviter");
        invitee = createUser(2L, "auth-user-2", "invitee@example.com", "Invitee");
        workspace = createWorkspace(1L, "Test Workspace", inviter);

        inviteRequest = WorkspaceInviteRequest.builder()
                .email("invitee@example.com")
                .build();
    }

    private User createUser(Long id, String authUserId, String email, String name) throws Exception {
        User user = User.builder()
                .authUserId(authUserId)
                .email(email)
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

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("워크스페이스 초대 생성 성공")
    void inviteUser_Success() {
        // given
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userService.findByAuthUserId("auth-user-1")).thenReturn(inviter);
        when(invitationRepository.existsByWorkspaceIdAndEmailAndStatus(1L, "invitee@example.com", InvitationStatus.PENDING))
                .thenReturn(false);
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(invocation -> {
            WorkspaceInvitation invitation = invocation.getArgument(0);
            try {
                setField(invitation, "id", 1L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return invitation;
        });

        // when
        var response = invitationService.inviteUser(1L, "auth-user-1", inviteRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("invitee@example.com");
        assertThat(response.getWorkspaceId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getToken()).isNotNull();
        assertThat(response.getExpiresAt()).isAfter(LocalDateTime.now());

        verify(workspaceRepository).findById(1L);
        verify(userService).findByAuthUserId("auth-user-1");
        verify(invitationRepository).existsByWorkspaceIdAndEmailAndStatus(1L, "invitee@example.com", InvitationStatus.PENDING);
        verify(invitationRepository).save(any(WorkspaceInvitation.class));
    }

    @Test
    @DisplayName("워크스페이스가 없으면 예외 발생")
    void inviteUser_WorkspaceNotFound() {
        // given
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> invitationService.inviteUser(1L, "auth-user-1", inviteRequest))
                .isInstanceOf(WorkspaceNotFoundException.class)
                .hasMessageContaining("Workspace not found");

        verify(workspaceRepository).findById(1L);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 PENDING 상태의 초대가 있으면 예외 발생")
    void inviteUser_DuplicateInvitation() {
        // given
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userService.findByAuthUserId("auth-user-1")).thenReturn(inviter);
        when(invitationRepository.existsByWorkspaceIdAndEmailAndStatus(1L, "invitee@example.com", InvitationStatus.PENDING))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> invitationService.inviteUser(1L, "auth-user-1", inviteRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("An invitation already exists");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("초대 수락 성공")
    void acceptInvitation_Success() {
        // given
        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .inviter(inviter)
                .email("invitee@example.com")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        try {
            setField(invitation, "id", 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(invitationRepository.findByToken("test-token")).thenReturn(Optional.of(invitation));
        when(userService.findByAuthUserIdOptional("auth-user-2")).thenReturn(Optional.of(invitee));
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(false);
        when(workspaceMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AcceptInvitationRequest request = AcceptInvitationRequest.builder()
                .token("test-token")
                .build();

        // when
        Long workspaceId = invitationService.acceptInvitation("auth-user-2", request);

        // then
        assertThat(workspaceId).isEqualTo(1L);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getAcceptedAt()).isNotNull();

        verify(invitationRepository).findByToken("test-token");
        verify(userService).findByAuthUserIdOptional("auth-user-2");
        verify(workspaceMemberRepository).existsByWorkspaceIdAndUserId(1L, 2L);
        verify(workspaceMemberRepository).save(any());
        verify(invitationRepository).save(invitation);
    }

    @Test
    @DisplayName("초대 토큰이 없으면 예외 발생")
    void acceptInvitation_TokenNotFound() {
        // given
        when(invitationRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        AcceptInvitationRequest request = AcceptInvitationRequest.builder()
                .token("invalid-token")
                .build();

        // when & then
        assertThatThrownBy(() -> invitationService.acceptInvitation("auth-user-2", request))
                .isInstanceOf(InvitationNotFoundException.class)
                .hasMessageContaining("Invitation not found");

        verify(invitationRepository).findByToken("invalid-token");
        verify(workspaceMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 초대는 수락할 수 없음")
    void acceptInvitation_Expired() {
        // given
        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .inviter(inviter)
                .email("invitee@example.com")
                .expiresAt(LocalDateTime.now().minusDays(1)) // 만료됨
                .build();
        try {
            setField(invitation, "id", 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(invitationRepository.findByToken("expired-token")).thenReturn(Optional.of(invitation));

        AcceptInvitationRequest request = AcceptInvitationRequest.builder()
                .token("expired-token")
                .build();

        // when & then
        assertThatThrownBy(() -> invitationService.acceptInvitation("auth-user-2", request))
                .isInstanceOf(InvitationExpiredException.class)
                .hasMessageContaining("Invitation has expired");

        verify(invitationRepository).findByToken("expired-token");
        verify(invitationRepository).save(invitation); // EXPIRED 상태로 저장
        verify(workspaceMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 수락된 초대는 다시 수락할 수 없음")
    void acceptInvitation_AlreadyAccepted() {
        // given
        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .inviter(inviter)
                .email("invitee@example.com")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        try {
            setField(invitation, "id", 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        invitation.accept(); // 이미 수락됨

        when(invitationRepository.findByToken("accepted-token")).thenReturn(Optional.of(invitation));

        AcceptInvitationRequest request = AcceptInvitationRequest.builder()
                .token("accepted-token")
                .build();

        // when & then
        assertThatThrownBy(() -> invitationService.acceptInvitation("auth-user-2", request))
                .isInstanceOf(InvitationAlreadyAcceptedException.class)
                .hasMessageContaining("Invitation has already been accepted");

        verify(invitationRepository).findByToken("accepted-token");
        verify(workspaceMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 워크스페이스 멤버인 경우 예외 발생")
    void acceptInvitation_AlreadyMember() {
        // given
        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .inviter(inviter)
                .email("invitee@example.com")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        try {
            setField(invitation, "id", 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(invitationRepository.findByToken("test-token")).thenReturn(Optional.of(invitation));
        when(userService.findByAuthUserIdOptional("auth-user-2")).thenReturn(Optional.of(invitee));
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(true); // 이미 멤버

        AcceptInvitationRequest request = AcceptInvitationRequest.builder()
                .token("test-token")
                .build();

        // when & then
        assertThatThrownBy(() -> invitationService.acceptInvitation("auth-user-2", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already a member");

        verify(workspaceMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용자가 없으면 자동 생성 후 초대 수락")
    void acceptInvitation_UserNotExists_CreatesUser() throws Exception {
        // given
        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .inviter(inviter)
                .email("newuser@example.com")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        try {
            setField(invitation, "id", 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        User newUser = createUser(3L, "auth-user-3", "newuser@example.com", "newuser");

        when(invitationRepository.findByToken("test-token")).thenReturn(Optional.of(invitation));
        when(userService.findByAuthUserIdOptional("auth-user-3")).thenReturn(Optional.empty());
        when(userService.createUser("auth-user-3", "newuser@example.com", "newuser")).thenReturn(newUser);
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 3L)).thenReturn(false);
        when(workspaceMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AcceptInvitationRequest request = AcceptInvitationRequest.builder()
                .token("test-token")
                .build();

        // when
        Long workspaceId = invitationService.acceptInvitation("auth-user-3", request);

        // then
        assertThat(workspaceId).isEqualTo(1L);
        verify(userService).createUser("auth-user-3", "newuser@example.com", "newuser");
        verify(workspaceMemberRepository).save(any());
    }
}

