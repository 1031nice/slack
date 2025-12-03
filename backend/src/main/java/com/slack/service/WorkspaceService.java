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
 * Workspace Domain Service
 * 
 * Workspace 도메인에 대한 비즈니스 로직을 처리합니다.
 * 다른 Domain Service와의 의존성을 최소화하여 순환 참조를 방지합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserService userService;
    private final ChannelService channelService;

    @Transactional
    public WorkspaceResponse createWorkspace(WorkspaceCreateRequest request, String authUserId) {
        User owner = userService.findByAuthUserId(authUserId);
        
        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .owner(owner)
                .build();
        
        Workspace saved = workspaceRepository.save(workspace);
        
        // 워크스페이스 생성자(owner)를 WorkspaceMember로 추가
        WorkspaceMember workspaceMember = WorkspaceMember.builder()
                .workspace(saved)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .build();
        workspaceMemberRepository.save(workspaceMember);
        
        // 워크스페이스 생성 시 기본 채널 생성
        channelService.findOrCreateDefaultChannel(saved, owner.getId());
        
        return toResponse(saved);
    }

    public WorkspaceResponse getWorkspaceById(Long id) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + id));
        return toResponse(workspace);
    }

    public List<WorkspaceResponse> getUserWorkspaces(String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        // 멤버십 기반으로 워크스페이스 조회 (v0.2)
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(user.getId());
        return memberships.stream()
                .map(WorkspaceMember::getWorkspace)
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

