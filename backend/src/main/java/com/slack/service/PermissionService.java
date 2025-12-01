package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelMember;
import com.slack.domain.channel.ChannelRole;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceMember;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.exception.ChannelNotFoundException;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.ChannelRepository;
import com.slack.repository.WorkspaceMemberRepository;
import com.slack.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한 체크를 담당하는 서비스
 * 
 * Workspace와 Channel에 대한 접근 권한을 확인합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;

    /**
     * 사용자가 특정 Workspace의 멤버인지 확인합니다.
     * 
     * @param userId 사용자 ID
     * @param workspaceId Workspace ID
     * @return 멤버인 경우 true
     */
    public boolean isWorkspaceMember(Long userId, Long workspaceId) {
        return workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
    }

    /**
     * 사용자가 특정 Workspace의 특정 역할 이상인지 확인합니다.
     * 
     * @param userId 사용자 ID
     * @param workspaceId Workspace ID
     * @param requiredRole 필요한 최소 역할
     * @return 권한이 있는 경우 true
     */
    public boolean hasWorkspaceRole(Long userId, Long workspaceId, WorkspaceRole requiredRole) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + workspaceId));

        // Workspace 소유자는 항상 모든 권한을 가짐
        if (workspace.getOwner().getId().equals(userId)) {
            return true;
        }

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElse(null);

        if (member == null) {
            return false;
        }

        return hasRoleOrHigher(member.getRole(), requiredRole);
    }

    /**
     * 사용자가 특정 Workspace의 소유자인지 확인합니다.
     * 
     * @param userId 사용자 ID
     * @param workspaceId Workspace ID
     * @return 소유자인 경우 true
     */
    public boolean isWorkspaceOwner(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + workspaceId));
        return workspace.getOwner().getId().equals(userId);
    }

    /**
     * 사용자가 특정 Channel에 접근할 수 있는지 확인합니다.
     * 
     * @param userId 사용자 ID
     * @param channelId Channel ID
     * @return 접근 가능한 경우 true
     */
    public boolean canAccessChannel(Long userId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ChannelNotFoundException("Channel not found with id: " + channelId));

        // Public Channel은 Workspace 멤버면 접근 가능
        if (channel.getType() == ChannelType.PUBLIC) {
            return isWorkspaceMember(userId, channel.getWorkspace().getId());
        }

        // Private Channel은 Channel 멤버만 접근 가능
        return channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
    }

    /**
     * 사용자가 특정 Channel의 멤버인지 확인합니다.
     * 
     * @param userId 사용자 ID
     * @param channelId Channel ID
     * @return 멤버인 경우 true
     */
    public boolean isChannelMember(Long userId, Long channelId) {
        return channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
    }

    /**
     * 사용자가 특정 Channel의 특정 역할 이상인지 확인합니다.
     * 
     * @param userId 사용자 ID
     * @param channelId Channel ID
     * @param requiredRole 필요한 최소 역할
     * @return 권한이 있는 경우 true
     */
    public boolean hasChannelRole(Long userId, Long channelId, ChannelRole requiredRole) {
        ChannelMember member = channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
                .orElse(null);

        if (member == null) {
            return false;
        }

        return hasRoleOrHigher(member.getRole(), requiredRole);
    }

    /**
     * 역할 계층을 확인합니다.
     * WorkspaceRole: OWNER > ADMIN > MEMBER
     */
    private boolean hasRoleOrHigher(WorkspaceRole userRole, WorkspaceRole requiredRole) {
        if (requiredRole == WorkspaceRole.MEMBER) {
            return true; // 모든 역할이 MEMBER 이상
        }
        if (requiredRole == WorkspaceRole.ADMIN) {
            return userRole == WorkspaceRole.ADMIN || userRole == WorkspaceRole.OWNER;
        }
        if (requiredRole == WorkspaceRole.OWNER) {
            return userRole == WorkspaceRole.OWNER;
        }
        return false;
    }

    /**
     * 역할 계층을 확인합니다.
     * ChannelRole: ADMIN > MEMBER
     */
    private boolean hasRoleOrHigher(ChannelRole userRole, ChannelRole requiredRole) {
        if (requiredRole == ChannelRole.MEMBER) {
            return true; // 모든 역할이 MEMBER 이상
        }
        if (requiredRole == ChannelRole.ADMIN) {
            return userRole == ChannelRole.ADMIN;
        }
        return false;
    }
}

