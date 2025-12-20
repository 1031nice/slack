package com.slack.service;

import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceMember;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.dto.workspace.WorkspaceCreateRequest;
import com.slack.dto.workspace.WorkspaceResponse;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.repository.WorkspaceMemberRepository;
import com.slack.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Workspace domain service.
 * Minimizes dependencies with other services to prevent circular references.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserService userService;
    private final ChannelService channelService;
    private final PermissionService permissionService;

    @Transactional
    public WorkspaceResponse createWorkspace(WorkspaceCreateRequest request, String authUserId) {
        User owner = userService.findByAuthUserId(authUserId);
        
        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .owner(owner)
                .build();
        
        Workspace saved = workspaceRepository.save(workspace);

        WorkspaceMember workspaceMember = WorkspaceMember.builder()
                .workspace(saved)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .build();
        workspaceMemberRepository.save(workspaceMember);

        channelService.findOrCreateDefaultChannel(saved, owner.getId());
        
        return toResponse(saved);
    }

    public WorkspaceResponse getWorkspaceById(Long id, Long userId) {
        permissionService.requireWorkspaceMember(userId, id);

        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + id));
        return toResponse(workspace);
    }

    public List<WorkspaceResponse> getUserWorkspaces(String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(user.getId());
        return memberships.stream()
                .map(WorkspaceMember::getWorkspace)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .ownerId(workspace.getOwner().getId())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }
}

