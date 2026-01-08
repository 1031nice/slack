package com.slack.message.controller;

import com.slack.user.domain.User;
import com.slack.user.service.UserService;
import com.slack.message.dto.MessageResponse;
import com.slack.message.service.MessageService;
import com.slack.unread.service.UnreadCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.slack.common.controller.ResponseHelper.ok;

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
    private final UserService userService;

    @GetMapping("/channels/{channelId}/messages")
    public ResponseEntity<List<MessageResponse>> getChannelMessages(
            @PathVariable Long channelId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long before,
            @AuthenticationPrincipal String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        unreadCountService.clearUnreadCount(user.getId(), channelId);

        List<MessageResponse> messages = messageService.getChannelMessages(channelId, user.getId(), limit, before);
        return ok(messages);
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> getMessageById(
            @PathVariable Long messageId,
            @AuthenticationPrincipal String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        MessageResponse response = messageService.getMessageById(messageId, user.getId());
        return ok(response);
    }
}

