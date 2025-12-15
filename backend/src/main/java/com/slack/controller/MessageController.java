package com.slack.controller;

import com.slack.domain.user.User;
import com.slack.dto.message.MessageResponse;
import com.slack.service.MessageService;
import com.slack.service.UnreadCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.slack.controller.ResponseHelper.ok;

/**
 * REST controller for message operations.
 * Message creation is handled via WebSocket only for real-time communication.
 * This controller provides read-only operations.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final UnreadCountService unreadCountService;

    @GetMapping("/channels/{channelId}/messages")
    @PreAuthorize("@permissionService.canAccessChannelByAuthUserId(authentication.principal.subject, #channelId)")
    public ResponseEntity<List<MessageResponse>> getChannelMessages(
            @PathVariable Long channelId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long before,
            @AuthenticationPrincipal User user) {
        unreadCountService.clearUnreadCount(user.getId(), channelId);

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

