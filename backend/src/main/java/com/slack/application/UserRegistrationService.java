package com.slack.application;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelMember;
import com.slack.domain.channel.ChannelRole;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceMember;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.WorkspaceMemberRepository;
import com.slack.service.ChannelService;
import com.slack.service.UserService;
import com.slack.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that orchestrates complex business logic across multiple domain services.
 * Prevents circular dependencies between domain services by coordinating their interactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final ChannelService channelService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelMemberRepository channelMemberRepository;

    /**
     * Finds an existing user by authUserId or creates a new user with default workspace and channel.
     * New users are automatically added to the default workspace and general channel.
     */
    @Transactional
    public User findOrCreateUser(String authUserId, String email, String name) {
        return userService.findByAuthUserIdOptional(authUserId)
                .orElseGet(() -> registerNewUser(authUserId, email, name));
    }

    private User registerNewUser(String authUserId, String email, String name) {
        User newUser = userService.createUser(authUserId, email, name);

        Workspace defaultWorkspace = workspaceService.findOrCreateDefaultWorkspace(newUser);
        Channel defaultChannel = channelService.findOrCreateDefaultChannel(
                defaultWorkspace, newUser.getId());

        addWorkspaceMember(defaultWorkspace, newUser);
        addChannelMember(defaultChannel, newUser);

        return newUser;
    }

    /**
     * Adds user to workspace. Handles duplicate insertion gracefully using DB unique constraint.
     */
    private void addWorkspaceMember(Workspace workspace, User user) {
        try {
            WorkspaceRole role = workspace.getRoleForUser(user.getId());
            WorkspaceMember member = WorkspaceMember.builder()
                    .workspace(workspace)
                    .user(user)
                    .role(role)
                    .build();
            workspaceMemberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            log.debug("WorkspaceMember already exists: workspace={}, user={}",
                    workspace.getId(), user.getId());
        }
    }

    /**
     * Adds user to channel. Handles duplicate insertion gracefully using DB unique constraint.
     */
    private void addChannelMember(Channel channel, User user) {
        try {
            ChannelMember member = ChannelMember.builder()
                    .channel(channel)
                    .user(user)
                    .role(ChannelRole.MEMBER)
                    .build();
            channelMemberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            log.debug("ChannelMember already exists: channel={}, user={}",
                    channel.getId(), user.getId());
        }
    }
}
