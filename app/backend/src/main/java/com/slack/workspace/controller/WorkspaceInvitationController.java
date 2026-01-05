package com.slack.workspace.controller;

import com.slack.user.domain.User;
import com.slack.workspace.dto.AcceptInvitationRequest;
import com.slack.workspace.dto.WorkspaceInviteRequest;
import com.slack.workspace.dto.WorkspaceInviteResponse;
import com.slack.workspace.service.WorkspaceInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.slack.common.controller.ResponseHelper.created;
import static com.slack.common.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceInvitationController {

    private final WorkspaceInvitationService invitationService;

    /**
     * 워크스페이스에 사용자를 초대합니다.
     * Owner 또는 Admin만 초대할 수 있습니다.
     */
    @PostMapping("/{workspaceId}/invitations")
    public ResponseEntity<WorkspaceInviteResponse> inviteUser(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceInviteRequest request,
            @AuthenticationPrincipal User user) {
        WorkspaceInviteResponse response = invitationService.inviteUser(workspaceId, user.getId(), request);
        return created(response);
    }

    /**
     * 초대를 수락합니다.
     * 인증된 사용자가 초대 토큰을 사용하여 워크스페이스에 가입합니다.
     */
    @PostMapping("/invitations/accept")
    public ResponseEntity<Long> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request,
            @AuthenticationPrincipal User user) {
        Long workspaceId = invitationService.acceptInvitation(user.getAuthUserId(), request);
        return ok(workspaceId);
    }
}

