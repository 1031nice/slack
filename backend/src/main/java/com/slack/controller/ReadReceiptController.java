package com.slack.controller;

import com.slack.service.ReadReceiptService;
import com.slack.service.UserService;
import com.slack.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.slack.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReadReceiptController {

    private final ReadReceiptService readReceiptService;
    private final UserService userService;

    /**
     * Get read receipt for a user in a channel
     * 
     * @param channelId Channel ID
     * @param jwt JWT token
     * @return Last read sequence number
     */
    @GetMapping("/channels/{channelId}/read-receipt")
    @PreAuthorize("@permissionService.canAccessChannelByAuthUserId(authentication.principal.subject, #channelId)")
    public ResponseEntity<Map<String, Long>> getReadReceipt(
            @PathVariable Long channelId,
            @AuthenticationPrincipal Jwt jwt) {
        JwtUtils.UserInfo userInfo = JwtUtils.extractUserInfo(jwt);
        var user = userService.findByAuthUserIdOptional(userInfo.authUserId())
                .orElseGet(() -> userService.createUser(
                        userInfo.authUserId(),
                        userInfo.email() != null ? userInfo.email() : userInfo.authUserId(),
                        userInfo.name()
                ));

        Long lastReadSequence = readReceiptService.getReadReceipt(user.getId(), channelId);
        return ok(Map.of("lastReadSequence", lastReadSequence != null ? lastReadSequence : 0L));
    }

    /**
     * Get all read receipts for a channel
     * 
     * @param channelId Channel ID
     * @return Map of userId -> lastReadSequence
     */
    @GetMapping("/channels/{channelId}/read-receipts")
    @PreAuthorize("@permissionService.canAccessChannelByAuthUserId(authentication.principal.subject, #channelId)")
    public ResponseEntity<Map<Long, Long>> getChannelReadReceipts(@PathVariable Long channelId) {
        Map<Long, Long> readReceipts = readReceiptService.getChannelReadReceipts(channelId);
        return ok(readReceipts);
    }
}
