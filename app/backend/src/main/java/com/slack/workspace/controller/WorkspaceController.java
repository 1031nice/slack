package com.slack.workspace.controller;

import com.slack.user.domain.User;
import com.slack.user.service.UserService;
import com.slack.workspace.dto.WorkspaceCreateRequest;
import com.slack.workspace.dto.WorkspaceResponse;
import com.slack.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.slack.common.controller.ResponseHelper.created;
import static com.slack.common.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody WorkspaceCreateRequest request,
            @AuthenticationPrincipal String authUserId) {
        WorkspaceResponse response = workspaceService.createWorkspace(request, authUserId);
        return created(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getUserWorkspaces(
            @AuthenticationPrincipal String authUserId) {
        List<WorkspaceResponse> workspaces = workspaceService.getUserWorkspaces(authUserId);
        return ok(workspaces);
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspaceById(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        WorkspaceResponse response = workspaceService.getWorkspaceById(workspaceId, user.getId());
        return ok(response);
    }
}

