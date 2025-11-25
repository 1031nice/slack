package com.slack.repository;

import com.slack.domain.workspace.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    /**
     * 특정 workspace에 특정 user가 멤버로 존재하는지 확인합니다.
     */
    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}

