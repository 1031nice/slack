package com.slack.workspace.controller;

import com.slack.user.domain.User;
import com.slack.user.service.UserService;
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
    private final UserService userService;

    @PostMapping("/{workspaceId}/invitations")
    public ResponseEntity<WorkspaceInviteResponse> inviteUser(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceInviteRequest request,
            @AuthenticationPrincipal String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        WorkspaceInviteResponse response = invitationService.inviteUser(workspaceId, user.getId(), request);
        return created(response);
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<Long> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request,
            @AuthenticationPrincipal String authUserId) {
        Long workspaceId = invitationService.acceptInvitation(authUserId, request);
        return ok(workspaceId);
    }
}

