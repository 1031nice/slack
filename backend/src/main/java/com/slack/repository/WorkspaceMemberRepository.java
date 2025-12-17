package com.slack.repository;

import com.slack.domain.workspace.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    /**
     * 특정 workspace에 특정 user가 멤버로 존재하는지 확인합니다.
     */
    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    /**
     * 특정 workspace와 user로 WorkspaceMember를 조회합니다.
     */
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    /**
     * 특정 user가 멤버로 있는 모든 WorkspaceMember를 조회합니다.
     */
    List<WorkspaceMember> findByUserId(Long userId);

    /**
     * 특정 workspace의 모든 멤버를 조회합니다.
     * 
     * @param workspaceId Workspace ID
     * @return List of WorkspaceMember for the workspace
     */
    List<WorkspaceMember> findByWorkspaceId(Long workspaceId);
}

