package com.slack.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceInviteResponse {
    private Long id;
    private Long workspaceId;
    private String email;
    private String token;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}

