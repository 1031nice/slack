package com.slack.controller;

import com.slack.domain.user.User;
import com.slack.dto.channel.ChannelCreateRequest;
import com.slack.dto.channel.ChannelResponse;
import com.slack.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.slack.controller.ResponseHelper.created;
import static com.slack.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping("/workspaces/{workspaceId}/channels")
    @PreAuthorize("@permissionService.isWorkspaceMemberByAuthUserId(authentication.principal.subject, #workspaceId)")
    public ResponseEntity<ChannelResponse> createChannel(
            @PathVariable Long workspaceId,
            @Valid @RequestBody ChannelCreateRequest request,
            @AuthenticationPrincipal User user) {
        ChannelResponse response = channelService.createChannel(workspaceId, request, user.getId());
        return created(response);
    }

    @GetMapping("/workspaces/{workspaceId}/channels")
    @PreAuthorize("@permissionService.isWorkspaceMemberByAuthUserId(authentication.principal.subject, #workspaceId)")
    public ResponseEntity<List<ChannelResponse>> getWorkspaceChannels(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal User user) {
        List<ChannelResponse> channels = channelService.getWorkspaceChannels(workspaceId, user.getId());
        return ok(channels);
    }

    @GetMapping("/channels/{channelId}")
    @PreAuthorize("@permissionService.canAccessChannelByAuthUserId(authentication.principal.subject, #channelId)")
    public ResponseEntity<ChannelResponse> getChannelById(
            @PathVariable Long channelId,
            @AuthenticationPrincipal User user) {
        ChannelResponse response = channelService.getChannelById(channelId, user.getId());
        return ok(response);
    }
}

