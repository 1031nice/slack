package com.slack.controller;

import com.slack.domain.user.User;
import com.slack.dto.workspace.WorkspaceCreateRequest;
import com.slack.dto.workspace.WorkspaceResponse;
import com.slack.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.slack.controller.ResponseHelper.created;
import static com.slack.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody WorkspaceCreateRequest request,
            @AuthenticationPrincipal User user) {
        WorkspaceResponse response = workspaceService.createWorkspace(request, user.getAuthUserId());
        return created(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getUserWorkspaces(
            @AuthenticationPrincipal User user) {
        List<WorkspaceResponse> workspaces = workspaceService.getUserWorkspaces(user.getAuthUserId());
        return ok(workspaces);
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("@permissionService.isWorkspaceMemberByAuthUserId(authentication.principal.subject, #workspaceId)")
    public ResponseEntity<WorkspaceResponse> getWorkspaceById(@PathVariable Long workspaceId) {
        WorkspaceResponse response = workspaceService.getWorkspaceById(workspaceId);
        return ok(response);
    }
}

