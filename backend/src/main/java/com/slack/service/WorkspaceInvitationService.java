package com.slack.service;

import com.slack.domain.user.User;
import com.slack.domain.workspace.InvitationStatus;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceInvitation;
import com.slack.domain.workspace.WorkspaceMember;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.dto.workspace.AcceptInvitationRequest;
import com.slack.dto.workspace.WorkspaceInviteRequest;
import com.slack.dto.workspace.WorkspaceInviteResponse;
import com.slack.exception.InvitationAlreadyAcceptedException;
import com.slack.exception.InvitationExpiredException;
import com.slack.exception.InvitationNotFoundException;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.repository.WorkspaceInvitationRepository;
import com.slack.repository.WorkspaceMemberRepository;
import com.slack.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    /**
     * 워크스페이스에 사용자를 초대합니다.
     * 
     * @param workspaceId 워크스페이스 ID
     * @param inviterAuthUserId 초대하는 사용자의 authUserId
     * @param request 초대 요청 (이메일)
     * @return 생성된 초대 정보
     */
    @Transactional
    public WorkspaceInviteResponse inviteUser(Long workspaceId, String inviterAuthUserId, WorkspaceInviteRequest request) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + workspaceId));
        
        User inviter = userService.findByAuthUserId(inviterAuthUserId);
        
        // 이미 PENDING 상태의 초대가 있는지 확인
        if (invitationRepository.existsByWorkspaceIdAndEmailAndStatus(workspaceId, request.getEmail(), InvitationStatus.PENDING)) {
            throw new IllegalArgumentException("An invitation already exists for this email in this workspace");
        }
        
        // TODO: 이메일로 사용자를 찾아서 이미 멤버인지 확인하는 로직 추가 필요
        // 현재는 초대 생성 시점에 중복 체크를 하지 않음 (수락 시점에 체크)
        
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITATION_EXPIRY_DAYS);
        
        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(workspace)
                .inviter(inviter)
                .email(request.getEmail())
                .expiresAt(expiresAt)
                .build();
        
        WorkspaceInvitation saved = invitationRepository.save(invitation);
        return toResponse(saved);
    }

    /**
     * 초대를 수락합니다.
     * 
     * @param authUserId 초대를 수락하는 사용자의 authUserId
     * @param request 초대 수락 요청 (토큰)
     * @return 수락된 워크스페이스 정보
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
        
        // 사용자 찾기 또는 생성
        User user = userService.findByAuthUserIdOptional(authUserId)
                .orElseGet(() -> {
                    // 사용자가 없으면 생성 (이메일로 생성)
                    return userService.createUser(authUserId, invitation.getEmail(), 
                            invitation.getEmail().split("@")[0]);
                });
        
        // 이미 워크스페이스 멤버인지 확인
        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(invitation.getWorkspace().getId(), user.getId())) {
            throw new IllegalArgumentException("User is already a member of this workspace");
        }
        
        // 초대 수락 처리
        invitation.accept();
        invitationRepository.save(invitation);
        
        // WorkspaceMember 생성
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

