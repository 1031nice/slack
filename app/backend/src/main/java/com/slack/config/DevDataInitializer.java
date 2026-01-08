package com.slack.config;

import com.slack.channel.domain.Channel;
import com.slack.channel.domain.ChannelMember;
import com.slack.channel.domain.ChannelRole;
import com.slack.channel.domain.ChannelType;
import com.slack.channel.repository.ChannelMemberRepository;
import com.slack.channel.repository.ChannelRepository;
import com.slack.user.domain.User;
import com.slack.user.repository.UserRepository;
import com.slack.workspace.domain.Workspace;
import com.slack.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Development environment data initializer.
 * Creates test users, workspaces, and channels if they don't exist.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Initializing development test data...");

        // Create test users
        User testUser = createUserIfNotExists("test-user", "test-user@dev.local", "Test User");
        User alice = createUserIfNotExists("alice", "alice@dev.local", "Alice");
        User bob = createUserIfNotExists("bob", "bob@dev.local", "Bob");

        // Create test workspace
        Workspace workspace = createWorkspaceIfNotExists(1L, "test-workspace", testUser);

        // Create test channel
        Channel channel = createChannelIfNotExists(1L, workspace, "test-channel", ChannelType.PUBLIC, testUser);

        // Add members to channel
        addChannelMemberIfNotExists(channel, testUser, ChannelRole.ADMIN);
        addChannelMemberIfNotExists(channel, alice, ChannelRole.MEMBER);
        addChannelMemberIfNotExists(channel, bob, ChannelRole.MEMBER);

        log.info("Development test data initialization complete");
        log.info("Available test users: test-user, alice, bob");
        log.info("Test workspace: {} (ID: {})", workspace.getName(), workspace.getId());
        log.info("Test channel: {} (ID: {})", channel.getName(), channel.getId());
    }

    private User createUserIfNotExists(String authUserId, String email, String name) {
        return userRepository.findByAuthUserId(authUserId)
                .orElseGet(() -> {
                    User user = User.builder()
                            .authUserId(authUserId)
                            .email(email)
                            .name(name)
                            .build();
                    user = userRepository.save(user);
                    log.info("Created test user: {} (ID: {})", authUserId, user.getId());
                    return user;
                });
    }

    private Workspace createWorkspaceIfNotExists(Long id, String name, User owner) {
        return workspaceRepository.findById(id)
                .orElseGet(() -> {
                    Workspace workspace = Workspace.builder()
                            .name(name)
                            .owner(owner)
                            .build();

                    // Manually set ID for test data consistency
                    try {
                        java.lang.reflect.Field idField = Workspace.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(workspace, id);
                    } catch (Exception e) {
                        log.warn("Could not set workspace ID, using auto-generated ID");
                    }

                    workspace = workspaceRepository.save(workspace);
                    log.info("Created test workspace: {} (ID: {})", name, workspace.getId());
                    return workspace;
                });
    }

    private Channel createChannelIfNotExists(Long id, Workspace workspace, String name,
                                            ChannelType type, User creator) {
        return channelRepository.findById(id)
                .orElseGet(() -> {
                    Channel channel = Channel.builder()
                            .workspace(workspace)
                            .name(name)
                            .type(type)
                            .createdBy(creator.getId())
                            .build();

                    // Manually set ID for test data consistency
                    try {
                        java.lang.reflect.Field idField = Channel.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(channel, id);
                    } catch (Exception e) {
                        log.warn("Could not set channel ID, using auto-generated ID");
                    }

                    channel = channelRepository.save(channel);
                    log.info("Created test channel: {} (ID: {})", name, channel.getId());
                    return channel;
                });
    }

    private void addChannelMemberIfNotExists(Channel channel, User user, ChannelRole role) {
        boolean exists = channelMemberRepository.existsByChannelIdAndUserId(channel.getId(), user.getId());
        if (!exists) {
            ChannelMember member = ChannelMember.builder()
                    .channel(channel)
                    .user(user)
                    .role(role)
                    .build();
            channelMemberRepository.save(member);
            log.info("Added user {} to channel {} with role {}", user.getAuthUserId(), channel.getName(), role);
        }
    }
}
