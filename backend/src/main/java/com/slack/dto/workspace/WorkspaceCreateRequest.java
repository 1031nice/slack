package com.slack.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceCreateRequest {
    
    @NotBlank(message = "Workspace name is required")
    @Size(min = 1, max = 255, message = "Workspace name must be between 1 and 255 characters")
    private String name;
    
    // ownerId는 제거됨 - JWT에서 authUserId를 추출하여 사용
}

