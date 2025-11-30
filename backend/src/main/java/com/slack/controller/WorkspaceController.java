package com.slack.controller;

import com.slack.dto.workspace.WorkspaceCreateRequest;
import com.slack.dto.workspace.WorkspaceResponse;
import com.slack.service.WorkspaceService;
import com.slack.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
        String authUserId = JwtUtils.extractAuthUserId(jwt);
        List<WorkspaceResponse> workspaces = workspaceService.getUserWorkspaces(authUserId);
        return ok(workspaces);
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspaceById(@PathVariable Long workspaceId) {
        WorkspaceResponse response = workspaceService.getWorkspaceById(workspaceId);
        return ok(response);
    }
}

