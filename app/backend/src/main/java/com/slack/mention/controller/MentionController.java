package com.slack.mention.controller;

import com.slack.user.domain.User;
import com.slack.user.service.UserService;
import com.slack.mention.dto.MentionResponse;
import com.slack.mention.service.MentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static com.slack.common.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MentionController {

    private final MentionService mentionService;
    private final UserService userService;

    /**
     * Get all mentions for the authenticated user
     *
     * @param authUserId Authenticated user ID
     * @return List of mentions
     */
    @GetMapping("/mentions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MentionResponse>> getMentions(@AuthenticationPrincipal String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        List<MentionResponse> mentions = mentionService.getMentionsByUserId(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ok(mentions);
    }

    /**
     * Get unread mentions for the authenticated user
     *
     * @param authUserId Authenticated user ID
     * @return List of unread mentions
     */
    @GetMapping("/mentions/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MentionResponse>> getUnreadMentions(@AuthenticationPrincipal String authUserId) {
        User user = userService.findByAuthUserId(authUserId);
        List<MentionResponse> mentions = mentionService.getUnreadMentionsByUserId(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ok(mentions);
    }

    /**
     * Mark a mention as read
     *
     * @param mentionId Mention ID
     * @return Success response
     */
    @PutMapping("/mentions/{mentionId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markMentionAsRead(@PathVariable Long mentionId) {
        mentionService.markMentionAsRead(mentionId);
        return ok(null);
    }

    private MentionResponse toResponse(com.slack.mention.domain.Mention mention) {
        return MentionResponse.builder()
                .id(mention.getId())
                .messageId(mention.getMessage().getId())
                .channelId(mention.getMessage().getChannel().getId())
                .mentionedUserId(mention.getMentionedUser().getId())
                .senderUserId(mention.getMessage().getUser().getId())
                .senderName(mention.getMessage().getUser().getName())
                .content(mention.getMessage().getContent())
                .createdAt(mention.getCreatedAt())
                .isRead(mention.getIsRead())
                .build();
    }
}
