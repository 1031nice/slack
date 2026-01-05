package com.slack.workspace.repository;

import com.slack.workspace.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    /**
     * 기본 workspace를 이름으로 찾습니다.
     * v0.1에서는 단일 기본 workspace를 사용합니다.
     */
    Optional<Workspace> findByName(String name);
}

