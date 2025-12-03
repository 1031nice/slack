package com.slack.controller;

import com.slack.application.UserRegistrationService;
import com.slack.dto.workspace.WorkspaceCreateRequest;
import com.slack.dto.workspace.WorkspaceResponse;
import com.slack.service.WorkspaceService;
import com.slack.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.slack.controller.ResponseHelper.created;
import static com.slack.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserRegistrationService userRegistrationService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody WorkspaceCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String authUserId = JwtUtils.extractAuthUserId(jwt);
        WorkspaceResponse response = workspaceService.createWorkspace(request, authUserId);
        return created(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getUserWorkspaces(
            @AuthenticationPrincipal Jwt jwt) {
        JwtUtils.UserInfo userInfo = JwtUtils.extractUserInfo(jwt);
        // 사용자가 없으면 자동으로 생성
        userRegistrationService.findOrCreateUser(userInfo.authUserId(), userInfo.email(), userInfo.name());
        List<WorkspaceResponse> workspaces = workspaceService.getUserWorkspaces(userInfo.authUserId());
        return ok(workspaces);
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("@permissionService.isWorkspaceMemberByAuthUserId(authentication.principal.subject, #workspaceId)")
    public ResponseEntity<WorkspaceResponse> getWorkspaceById(@PathVariable Long workspaceId) {
        WorkspaceResponse response = workspaceService.getWorkspaceById(workspaceId);
        return ok(response);
    }
}

