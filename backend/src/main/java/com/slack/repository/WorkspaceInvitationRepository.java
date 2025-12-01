package com.slack.repository;

import com.slack.domain.workspace.InvitationStatus;
import com.slack.domain.workspace.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, Long> {
    /**
     * 토큰으로 초대를 조회합니다.
     */
    Optional<WorkspaceInvitation> findByToken(String token);

    /**
     * 특정 workspace와 email로 PENDING 상태의 초대가 존재하는지 확인합니다.
     */
    boolean existsByWorkspaceIdAndEmailAndStatus(Long workspaceId, String email, InvitationStatus status);
}

