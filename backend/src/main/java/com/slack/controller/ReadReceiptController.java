package com.slack.controller;

import com.slack.domain.user.User;
import com.slack.service.ReadReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
     * @param user Authenticated user
     * @return Last read timestamp
     */
    @GetMapping("/channels/{channelId}/read-receipt")
    public ResponseEntity<Map<String, String>> getReadReceipt(
            @PathVariable Long channelId,
            @AuthenticationPrincipal User user) {
        String lastReadTimestamp = readReceiptService.getReadReceipt(user.getId(), channelId);
        return ok(Map.of("lastReadTimestamp", lastReadTimestamp != null ? lastReadTimestamp : "0"));
    }

    /**
     * Get all read receipts for a channel
     *
     * @param channelId Channel ID
     * @param user Authenticated user
     * @return Map of userId -> lastReadTimestamp
     */
    @GetMapping("/channels/{channelId}/read-receipts")
    public ResponseEntity<Map<Long, String>> getChannelReadReceipts(
            @PathVariable Long channelId,
            @AuthenticationPrincipal User user) {
        Map<Long, String> readReceipts = readReceiptService.getChannelReadReceipts(channelId, user.getId());
        return ok(readReceipts);
    }
}
