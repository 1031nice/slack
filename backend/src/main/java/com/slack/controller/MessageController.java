package com.slack.controller;

import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
import com.slack.service.MessageService;
import com.slack.service.UnreadCountService;
import com.slack.service.UserService;
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
public class MessageController {

    private final MessageService messageService;
    private final UnreadCountService unreadCountService;
    private final UserService userService;

    @PostMapping("/channels/{channelId}/messages")
    @PreAuthorize("@permissionService.canAccessChannelByAuthUserId(authentication.principal.subject, #channelId)")
    public ResponseEntity<MessageResponse> createMessage(
            @PathVariable Long channelId,
            @Valid @RequestBody MessageCreateRequest request) {
        MessageResponse response = messageService.createMessage(channelId, request);
        return created(response);
    }

    @GetMapping("/channels/{channelId}/messages")
    @PreAuthorize("@permissionService.canAccessChannelByAuthUserId(authentication.principal.subject, #channelId)")
    public ResponseEntity<List<MessageResponse>> getChannelMessages(
            @PathVariable Long channelId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long before,
            @AuthenticationPrincipal Jwt jwt) {
        // Clear unread count when user reads channel messages
        if (jwt != null) {
            String authUserId = jwt.getSubject();
            try {
                var user = userService.findByAuthUserId(authUserId);
                unreadCountService.clearUnreadCount(user.getId(), channelId);
            } catch (Exception e) {
                // If user not found, skip unread count clearing
                // This can happen if user hasn't been registered yet
            }
        }
        
        List<MessageResponse> messages = messageService.getChannelMessages(channelId, limit, before);
        return ok(messages);
    }

    @GetMapping("/messages/{messageId}")
    @PreAuthorize("@permissionService.canAccessMessageByAuthUserId(authentication.principal.subject, #messageId)")
    public ResponseEntity<MessageResponse> getMessageById(@PathVariable Long messageId) {
        MessageResponse response = messageService.getMessageById(messageId);
        return ok(response);
    }
}

