package com.slack.controller;

import com.slack.dto.channel.ChannelCreateRequest;
import com.slack.dto.channel.ChannelResponse;
import com.slack.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping("/workspaces/{workspaceId}/channels")
    public ResponseEntity<ChannelResponse> createChannel(
            @PathVariable Long workspaceId,
            @Valid @RequestBody ChannelCreateRequest request) {
        ChannelResponse response = channelService.createChannel(workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workspaces/{workspaceId}/channels")
    public ResponseEntity<List<ChannelResponse>> getWorkspaceChannels(@PathVariable Long workspaceId) {
        List<ChannelResponse> channels = channelService.getWorkspaceChannels(workspaceId);
        return ResponseEntity.ok(channels);
    }

    @GetMapping("/channels/{channelId}")
    public ResponseEntity<ChannelResponse> getChannelById(@PathVariable Long channelId) {
        ChannelResponse response = channelService.getChannelById(channelId);
        return ResponseEntity.ok(response);
    }
}

