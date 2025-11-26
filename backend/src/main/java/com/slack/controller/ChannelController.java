package com.slack.controller;

import com.slack.dto.channel.ChannelCreateRequest;
import com.slack.dto.channel.ChannelResponse;
import com.slack.service.ChannelService;
import com.slack.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final UserService userService;

    @PostMapping("/workspaces/{workspaceId}/channels")
    public ResponseEntity<ChannelResponse> createChannel(
            @PathVariable Long workspaceId,
            @Valid @RequestBody ChannelCreateRequest request) {
        ChannelResponse response = channelService.createChannel(workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workspaces/{workspaceId}/channels")
    public ResponseEntity<List<ChannelResponse>> getWorkspaceChannels(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal Jwt jwt) {
        // JWT에서 사용자 정보 추출 및 사용자 생성/조회
        String authUserId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        if (email == null) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (name == null) {
            name = email != null ? email.split("@")[0] : "User";
        }
        
        // 사용자 생성/조회 (기본 워크스페이스/채널 자동 생성)
        userService.findOrCreateByAuthUserId(authUserId, email != null ? email : authUserId, name);
        
        List<ChannelResponse> channels = channelService.getWorkspaceChannels(workspaceId);
        return ResponseEntity.ok(channels);
    }

    @GetMapping("/channels/{channelId}")
    public ResponseEntity<ChannelResponse> getChannelById(@PathVariable Long channelId) {
        ChannelResponse response = channelService.getChannelById(channelId);
        return ResponseEntity.ok(response);
    }
}

