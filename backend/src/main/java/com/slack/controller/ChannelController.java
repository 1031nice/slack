package com.slack.controller;

import com.slack.application.UserRegistrationService;
import com.slack.dto.channel.ChannelCreateRequest;
import com.slack.dto.channel.ChannelResponse;
import com.slack.service.ChannelService;
import com.slack.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.slack.controller.ResponseHelper.created;
import static com.slack.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final UserRegistrationService userRegistrationService;

    @PostMapping("/workspaces/{workspaceId}/channels")
    @PreAuthorize("@permissionService.isWorkspaceMemberByAuthUserId(authentication.principal.subject, #workspaceId)")
    public ResponseEntity<ChannelResponse> createChannel(
            @PathVariable Long workspaceId,
            @Valid @RequestBody ChannelCreateRequest request) {
        ChannelResponse response = channelService.createChannel(workspaceId, request);
        return created(response);
    }

    @GetMapping("/workspaces/{workspaceId}/channels")
    @PreAuthorize("@permissionService.isWorkspaceMemberByAuthUserId(authentication.principal.subject, #workspaceId)")
    public ResponseEntity<List<ChannelResponse>> getWorkspaceChannels(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal Jwt jwt) {
        // JWT에서 사용자 정보 추출 및 사용자 생성/조회
        JwtUtils.UserInfo userInfo = JwtUtils.extractUserInfo(jwt);
        
        // 사용자 생성/조회 (기본 워크스페이스/채널 자동 생성)
        var user = userRegistrationService.findOrCreateUser(
                userInfo.authUserId(),
                userInfo.email() != null ? userInfo.email() : userInfo.authUserId(),
                userInfo.name()
        );
        
        // 사용자가 접근 가능한 채널만 필터링하여 반환
        List<ChannelResponse> channels = channelService.getWorkspaceChannels(workspaceId, user.getId());
        return ok(channels);
    }

    @GetMapping("/channels/{channelId}")
    @PreAuthorize("@permissionService.canAccessChannelByAuthUserId(authentication.principal.subject, #channelId)")
    public ResponseEntity<ChannelResponse> getChannelById(
            @PathVariable Long channelId,
            @AuthenticationPrincipal Jwt jwt) {
        // JWT에서 사용자 정보 추출
        JwtUtils.UserInfo userInfo = JwtUtils.extractUserInfo(jwt);
        var user = userRegistrationService.findOrCreateUser(
                userInfo.authUserId(),
                userInfo.email() != null ? userInfo.email() : userInfo.authUserId(),
                userInfo.name()
        );
        
        ChannelResponse response = channelService.getChannelById(channelId, user.getId());
        return ok(response);
    }
}

