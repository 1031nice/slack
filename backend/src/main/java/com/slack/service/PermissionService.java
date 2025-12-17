package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.workspace.Workspace;
import com.slack.exception.ChannelNotFoundException;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.ChannelRepository;
import com.slack.repository.MessageRepository;
import com.slack.repository.WorkspaceMemberRepository;
import com.slack.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for checking permissions.
 * Used internally by service layer, NOT by @PreAuthorize.
 *
 * Design: Explicit, type-safe, loggable authorization checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MessageRepository messageRepository;

    /**
     * Check if user is a workspace member.
     * Throws AccessDeniedException if not.
     */
    public void requireWorkspaceMember(Long userId, Long workspaceId) {
        if (!isWorkspaceMember(userId, workspaceId)) {
            log.warn("Access denied: user={} is not a member of workspace={}", userId, workspaceId);
            throw new AccessDeniedException("User is not a member of this workspace");
        }
    }

    /**
     * Check if user can access a channel.
     * - PUBLIC channels: must be workspace member
     * - PRIVATE channels: must be channel member
     * Throws AccessDeniedException if not.
     */
    public void requireChannelAccess(Long userId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ChannelNotFoundException("Channel not found with id: " + channelId));

        boolean canAccess;
        if (channel.getType() == ChannelType.PUBLIC) {
            canAccess = isWorkspaceMember(userId, channel.getWorkspace().getId());
        } else {
            canAccess = isChannelMember(userId, channelId);
        }

        if (!canAccess) {
            log.warn("Access denied: user={} cannot access channel={} (type={})",
                    userId, channelId, channel.getType());
            throw new AccessDeniedException("User cannot access this channel");
        }
    }

    /**
     * Check if user has workspace role (ADMIN or OWNER).
     * Throws AccessDeniedException if not.
     */
    public void requireWorkspaceAdmin(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + workspaceId));

        // Workspace owner always has admin access
        if (workspace.getOwner().getId().equals(userId)) {
            return;
        }

        // Check if user is workspace member with ADMIN role
        boolean isAdmin = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(member -> member.getRole().name().equals("ADMIN"))
                .orElse(false);

        if (!isAdmin) {
            log.warn("Access denied: user={} is not an admin of workspace={}", userId, workspaceId);
            throw new AccessDeniedException("User does not have admin access to this workspace");
        }
    }

    /**
     * Check if user can access a message (via its channel).
     * Throws AccessDeniedException if not.
     */
    public void requireMessageAccess(Long userId, Long messageId) {
        Long channelId = messageRepository.findById(messageId)
                .map(message -> message.getChannel().getId())
                .orElseThrow(() -> new ChannelNotFoundException("Message not found with id: " + messageId));

        requireChannelAccess(userId, channelId);
    }

    // ========== Private helper methods ==========

    private boolean isWorkspaceMember(Long userId, Long workspaceId) {
        return workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
    }

    private boolean isChannelMember(Long userId, Long channelId) {
        return channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
    }
}
