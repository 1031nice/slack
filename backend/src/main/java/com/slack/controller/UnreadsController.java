package com.slack.controller;

import com.slack.domain.user.User;
import com.slack.dto.unread.UnreadsViewResponse;
import com.slack.service.UnreadsViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.slack.controller.ResponseHelper.ok;

/**
 * REST controller for unreads view operations.
 * Provides aggregated view of all unread messages across channels.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UnreadsController {

    private final UnreadsViewService unreadsViewService;

    /**
     * Get aggregated unread messages across all channels for the authenticated user.
     *
     * @param sort Sort option: "newest" (default), "oldest", or "channel"
     * @param limit Maximum number of messages to return (default: 50, max: 200)
     * @param user Authenticated user
     * @return UnreadsViewResponse with sorted unread messages
     */
    @GetMapping("/unreads")
    public ResponseEntity<UnreadsViewResponse> getUnreads(
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User user) {
        UnreadsViewResponse response = unreadsViewService.getUnreads(user.getId(), sort, limit);
        return ok(response);
    }
}

