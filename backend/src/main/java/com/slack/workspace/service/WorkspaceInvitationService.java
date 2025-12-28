package com.slack.workspace.service;

import com.slack.user.domain.User;
import com.slack.workspace.domain.InvitationStatus;
import com.slack.workspace.domain.Workspace;
import com.slack.workspace.domain.WorkspaceInvitation;
import com.slack.workspace.domain.WorkspaceMember;
import com.slack.workspace.domain.WorkspaceRole;
import com.slack.workspace.dto.AcceptInvitationRequest;
import com.slack.workspace.dto.WorkspaceInviteRequest;
import com.slack.workspace.dto.WorkspaceInviteResponse;
import com.slack.exception.InvitationAlreadyAcceptedException;
import com.slack.exception.InvitationExpiredException;
import com.slack.exception.InvitationNotFoundException;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.workspace.repository.WorkspaceInvitationRepository;
import com.slack.workspace.repository.WorkspaceMemberRepository;
import com.slack.workspace.repository.WorkspaceRepository;
import com.slack.workspace.service.notification.InvitationNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.slack.user.service.UserService;
import com.slack.common.service.PermissionService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceInvitationService {

    private static final int INVITATION_EXPIRY_DAYS = 7;

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserService userService;
    private final PermissionService permissionService;
    private final InvitationNotifier invitationNotifier;

    /**
     * Invites a user to a workspace.
     * Only workspace admins can invite users.
     */
    @Transactional
    public WorkspaceInviteResponse inviteUser(Long workspaceId, Long inviterId, WorkspaceInviteRequest request) {
        permissionService.requireWorkspaceAdmin(inviterId, workspaceId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + workspaceId));

        User inviter = userService.findById(inviterId);

        if (invitationRepository.existsByWorkspaceIdAndEmailAndStatus(workspaceId, request.getEmail(), InvitationStatus.PENDING)) {
            throw new IllegalArgumentException("An invitation already exists for this email in this workspace");
        }

        userService.findByEmail(request.getEmail()).ifPresent(existingUser -> {
            if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, existingUser.getId())) {
                throw new IllegalArgumentException("User is already a member of this workspace");
            }
        });

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITATION_EXPIRY_DAYS);
        
        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .inviter(inviter)
                .email(request.getEmail())
                .expiresAt(expiresAt)
                .build();
        
        WorkspaceInvitation saved = invitationRepository.save(invitation);
        invitationNotifier.sendInvitation(saved);
        return toResponse(saved);
    }

    /**
     * Accepts a workspace invitation.
     * Creates a new user if they don't exist yet.
     */
    @Transactional
    public Long acceptInvitation(String authUserId, AcceptInvitationRequest request) {
        WorkspaceInvitation invitation = invitationRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found with token: " + request.getToken()));
        
        if (!invitation.isPending()) {
            throw new InvitationAlreadyAcceptedException("Invitation has already been accepted or cancelled");
        }
        
        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new InvitationExpiredException("Invitation has expired");
        }

        User user = userService.findByAuthUserIdOptional(authUserId)
                .orElseGet(() -> userService.createUser(authUserId, invitation.getEmail(),
                        invitation.getEmail().split("@")[0]));

        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(invitation.getWorkspace().getId(), user.getId())) {
            throw new IllegalArgumentException("User is already a member of this workspace");
        }

        invitation.accept();
        invitationRepository.save(invitation);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(invitation.getWorkspace())
                .user(user)
                .role(WorkspaceRole.MEMBER)
                .build();
        workspaceMemberRepository.save(member);
        
        return invitation.getWorkspace().getId();
    }

    private WorkspaceInviteResponse toResponse(WorkspaceInvitation invitation) {
        return WorkspaceInviteResponse.builder()
                .id(invitation.getId())
                .workspaceId(invitation.getWorkspace().getId())
                .email(invitation.getEmail())
                .token(invitation.getToken())
                .status(invitation.getStatus().name())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}

