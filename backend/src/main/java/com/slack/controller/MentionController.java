package com.slack.controller;

import com.slack.dto.mention.MentionResponse;
import com.slack.service.MentionService;
import com.slack.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static com.slack.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MentionController {

    private final MentionService mentionService;
    private final com.slack.application.UserRegistrationService userRegistrationService;

    /**
     * Get all mentions for the authenticated user
     * 
     * @param jwt JWT token
     * @return List of mentions
     */
    @GetMapping("/mentions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MentionResponse>> getMentions(@AuthenticationPrincipal Jwt jwt) {
        JwtUtils.UserInfo userInfo = JwtUtils.extractUserInfo(jwt);
        var user = userRegistrationService.findOrCreateUser(
                userInfo.authUserId(),
                userInfo.email() != null ? userInfo.email() : userInfo.authUserId(),
                userInfo.name()
        );

        List<MentionResponse> mentions = mentionService.getMentionsByUserId(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ok(mentions);
    }

    /**
     * Get unread mentions for the authenticated user
     * 
     * @param jwt JWT token
     * @return List of unread mentions
     */
    @GetMapping("/mentions/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MentionResponse>> getUnreadMentions(@AuthenticationPrincipal Jwt jwt) {
        JwtUtils.UserInfo userInfo = JwtUtils.extractUserInfo(jwt);
        var user = userRegistrationService.findOrCreateUser(
                userInfo.authUserId(),
                userInfo.email() != null ? userInfo.email() : userInfo.authUserId(),
                userInfo.name()
        );

        List<MentionResponse> mentions = mentionService.getUnreadMentionsByUserId(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ok(mentions);
    }

    /**
     * Mark a mention as read
     * 
     * @param mentionId Mention ID
     * @param jwt JWT token
     * @return Success response
     */
    @PutMapping("/mentions/{mentionId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markMentionAsRead(
            @PathVariable Long mentionId,
            @AuthenticationPrincipal Jwt jwt) {
        mentionService.markMentionAsRead(mentionId);
        return ok(null);
    }

    private MentionResponse toResponse(com.slack.domain.mention.Mention mention) {
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
