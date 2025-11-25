package com.slack.service;

import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.dto.workspace.WorkspaceCreateRequest;
import com.slack.dto.workspace.WorkspaceResponse;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final UserService userService;

    @Transactional
    public WorkspaceResponse createWorkspace(WorkspaceCreateRequest request, String authUserId) {
        User owner = userService.findByAuthUserId(authUserId);
        
        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .owner(owner)
                .build();
        
        Workspace saved = workspaceRepository.save(workspace);
        return toResponse(saved);
    }

    public WorkspaceResponse getWorkspaceById(Long id) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + id));
        return toResponse(workspace);
    }

    public List<WorkspaceResponse> getUserWorkspaces(String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        return workspaceRepository.findAll().stream()
                .filter(workspace -> workspace.getOwner().getId().equals(user.getId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 기본 workspace를 찾거나 생성합니다.
     * v0.1에서는 단일 기본 workspace를 사용하며, 첫 사용자가 owner가 됩니다.
     * 
     * @param owner 기본 workspace가 없을 경우 생성할 owner
     * @return 기본 workspace
     */
    @Transactional
    public Workspace findOrCreateDefaultWorkspace(User owner) {
        final String DEFAULT_WORKSPACE_NAME = "Default Workspace";
        
        return workspaceRepository.findByName(DEFAULT_WORKSPACE_NAME)
                .orElseGet(() -> {
                    Workspace defaultWorkspace = Workspace.builder()
                            .name(DEFAULT_WORKSPACE_NAME)
                            .owner(owner)
                            .build();
                    return workspaceRepository.save(defaultWorkspace);
                });
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

