package com.slack.controller;

import com.slack.dto.workspace.AcceptInvitationRequest;
import com.slack.dto.workspace.WorkspaceInviteRequest;
import com.slack.dto.workspace.WorkspaceInviteResponse;
import com.slack.service.WorkspaceInvitationService;
import com.slack.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import static com.slack.controller.ResponseHelper.created;
import static com.slack.controller.ResponseHelper.ok;

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
    @PreAuthorize("@permissionService.hasWorkspaceRoleByAuthUserId(authentication.principal.subject, #workspaceId, T(com.slack.domain.workspace.WorkspaceRole).ADMIN)")
    public ResponseEntity<WorkspaceInviteResponse> inviteUser(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceInviteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String authUserId = JwtUtils.extractAuthUserId(jwt);
        WorkspaceInviteResponse response = invitationService.inviteUser(workspaceId, authUserId, request);
        return created(response);
    }

    /**
     * 초대를 수락합니다.
     * 인증된 사용자가 초대 토큰을 사용하여 워크스페이스에 가입합니다.
     */
    @PostMapping("/invitations/accept")
    public ResponseEntity<Long> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String authUserId = JwtUtils.extractAuthUserId(jwt);
        Long workspaceId = invitationService.acceptInvitation(authUserId, request);
        return ok(workspaceId);
    }
}

