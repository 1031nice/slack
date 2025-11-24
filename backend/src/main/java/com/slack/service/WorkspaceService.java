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

