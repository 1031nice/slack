package com.slack.controller;

import com.slack.domain.user.User;
import com.slack.service.ReadReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.slack.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReadReceiptController {

    private final ReadReceiptService readReceiptService;

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
            @AuthenticationPrincipal User user) {
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
