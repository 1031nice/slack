package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelMember;
import com.slack.domain.channel.ChannelRole;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.domain.workspace.WorkspaceMember;
import com.slack.domain.workspace.WorkspaceRole;
import com.slack.exception.UserNotFoundException;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.UserRepository;
import com.slack.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    // TODO: [기술 부채] 같은 레이어의 Domain Service 간 의존성 제거 필요
    // 현재: UserService → WorkspaceService, ChannelService (순환 참조 위험)
    // 해결: Application Service 레이어 추가하여 UserRegistrationService에서 조율
    // 참고: 같은 레이어의 서비스 간 의존성은 스파게티 코드로 이어질 수 있음
    @Lazy
    private final WorkspaceService workspaceService;
    @Lazy
    private final ChannelService channelService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelMemberRepository channelMemberRepository;

    /**
     * authUserId로 User를 찾거나, 없으면 생성합니다.
     * JWT의 sub 클레임과 email, name을 사용합니다.
     * 
     * 새 사용자가 생성되면 기본 workspace와 channel에 자동으로 추가됩니다.
     * v0.1에서는 단일 기본 workspace를 사용하며, 첫 사용자가 owner가 됩니다.
     */
    @Transactional
    public User findOrCreateByAuthUserId(String authUserId, String email, String name) {
        return userRepository.findByAuthUserId(authUserId)
                .orElseGet(() -> {
                    // 새 사용자 생성
                    User newUser = User.builder()
                            .authUserId(authUserId)
                            .email(email)
                            .name(name)
                            .build();
                    User savedUser = userRepository.save(newUser);
                    
                    // 기본 workspace 찾거나 생성
                    Workspace defaultWorkspace = workspaceService.findOrCreateDefaultWorkspace(savedUser);
                    
                    // 기본 channel 찾거나 생성
                    Channel defaultChannel = channelService.findOrCreateDefaultChannel(
                            defaultWorkspace, savedUser.getId());
                    
                    // WorkspaceMember 생성 (이미 존재하는지 확인)
                    if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(
                            defaultWorkspace.getId(), savedUser.getId())) {
                        WorkspaceRole workspaceRole = defaultWorkspace.getOwner().getId().equals(savedUser.getId())
                                ? WorkspaceRole.OWNER
                                : WorkspaceRole.MEMBER;
                        
                        WorkspaceMember workspaceMember = WorkspaceMember.builder()
                                .workspace(defaultWorkspace)
                                .user(savedUser)
                                .role(workspaceRole)
                                .build();
                        workspaceMemberRepository.save(workspaceMember);
                    }
                    
                    // ChannelMember 생성 (이미 존재하는지 확인)
                    if (!channelMemberRepository.existsByChannelIdAndUserId(
                            defaultChannel.getId(), savedUser.getId())) {
                        ChannelMember channelMember = ChannelMember.builder()
                                .channel(defaultChannel)
                                .user(savedUser)
                                .role(ChannelRole.MEMBER)
                                .build();
                        channelMemberRepository.save(channelMember);
                    }
                    
                    return savedUser;
                });
    }

    public User findByAuthUserId(String authUserId) {
        return userRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found with authUserId: " + authUserId));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }
}

