package com.slack.controller;

import com.slack.dto.workspace.WorkspaceCreateRequest;
import com.slack.dto.workspace.WorkspaceResponse;
import com.slack.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody WorkspaceCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String authUserId = jwt.getClaimAsString("sub");
        WorkspaceResponse response = workspaceService.createWorkspace(request, authUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getUserWorkspaces(
            @AuthenticationPrincipal Jwt jwt) {
        String authUserId = jwt.getClaimAsString("sub");
        List<WorkspaceResponse> workspaces = workspaceService.getUserWorkspaces(authUserId);
        return ResponseEntity.ok(workspaces);
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspaceById(@PathVariable Long workspaceId) {
        WorkspaceResponse response = workspaceService.getWorkspaceById(workspaceId);
        return ResponseEntity.ok(response);
    }
}

