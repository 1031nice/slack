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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service 레이어
 * 
 * 복합 비즈니스 로직을 조율하는 서비스입니다.
 * 여러 Domain Service를 조합하여 사용자 등록과 같은 복합 작업을 처리합니다.
 * 
 * 이 레이어를 통해 Domain Service 간의 순환 참조를 방지하고,
 * 각 Domain Service는 자신의 도메인에만 집중할 수 있습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserRegistrationService {

    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final ChannelService channelService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelMemberRepository channelMemberRepository;

    /**
     * authUserId로 User를 찾거나, 없으면 생성합니다.
     * JWT의 sub 클레임과 email, name을 사용합니다.
     * 
     * 새 사용자가 생성되면 기본 workspace와 channel에 자동으로 추가됩니다.
     * v0.1에서는 단일 기본 workspace를 사용하며, 첫 사용자가 owner가 됩니다.
     * 
     * @param authUserId 인증 서버의 사용자 ID
     * @param email 사용자 이메일
     * @param name 사용자 이름
     * @return 찾거나 생성된 User
     */
    public User findOrCreateUser(String authUserId, String email, String name) {
        // 기존 사용자 조회
        return userService.findByAuthUserIdOptional(authUserId)
                .orElseGet(() -> registerNewUser(authUserId, email, name));
    }

    /**
     * 새 사용자를 등록하고 기본 workspace와 channel에 추가합니다.
     */
    private User registerNewUser(String authUserId, String email, String name) {
        // 새 사용자 생성
        User newUser = userService.createUser(authUserId, email, name);
        
        // 기본 workspace 찾거나 생성
        Workspace defaultWorkspace = workspaceService.findOrCreateDefaultWorkspace(newUser);
        
        // 기본 channel 찾거나 생성
        Channel defaultChannel = channelService.findOrCreateDefaultChannel(
                defaultWorkspace, newUser.getId());
        
        // WorkspaceMember 생성 (이미 존재하는지 확인)
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(
                defaultWorkspace.getId(), newUser.getId())) {
            WorkspaceRole workspaceRole = defaultWorkspace.getOwner().getId().equals(newUser.getId())
                    ? WorkspaceRole.OWNER
                    : WorkspaceRole.MEMBER;
            
            WorkspaceMember workspaceMember = WorkspaceMember.builder()
                    .workspace(defaultWorkspace)
                    .user(newUser)
                    .role(workspaceRole)
                    .build();
            workspaceMemberRepository.save(workspaceMember);
        }
        
        // ChannelMember 생성 (이미 존재하는지 확인)
        if (!channelMemberRepository.existsByChannelIdAndUserId(
                defaultChannel.getId(), newUser.getId())) {
            ChannelMember channelMember = ChannelMember.builder()
                    .channel(defaultChannel)
                    .user(newUser)
                    .role(ChannelRole.MEMBER)
                    .build();
            channelMemberRepository.save(channelMember);
        }
        
        return newUser;
    }
}

