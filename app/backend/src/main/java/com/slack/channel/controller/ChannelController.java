package com.slack.channel.controller;

import com.slack.user.domain.User;
import com.slack.user.service.UserService;
import com.slack.channel.dto.ChannelCreateRequest;
import com.slack.channel.dto.ChannelResponse;
import com.slack.channel.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

import static com.slack.common.controller.ResponseHelper.created;
import static com.slack.common.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final UserService userService;

    @PostMapping("/workspaces/{workspaceId}/channels")
    public ResponseEntity<ChannelResponse> createChannel(
            @PathVariable Long workspaceId,
            @Valid @RequestBody ChannelCreateRequest request,
            Principal principal) {
        String authUserId = principal.getName();
        User user = userService.findByAuthUserId(authUserId);
        ChannelResponse response = channelService.createChannel(workspaceId, request, user.getId());
        return created(response);
    }

    @GetMapping("/workspaces/{workspaceId}/channels")
    public ResponseEntity<List<ChannelResponse>> getWorkspaceChannels(
            @PathVariable Long workspaceId,
            Principal principal) {
        String authUserId = principal.getName();
        User user = userService.findByAuthUserId(authUserId);
        List<ChannelResponse> channels = channelService.getWorkspaceChannels(workspaceId, user.getId());
        return ok(channels);
    }

    @GetMapping("/channels/{channelId}")
    public ResponseEntity<ChannelResponse> getChannelById(
            @PathVariable Long channelId,
            Principal principal) {
        String authUserId = principal.getName();
        User user = userService.findByAuthUserId(authUserId);
        ChannelResponse response = channelService.getChannelById(channelId, user.getId());
        return ok(response);
    }
}

