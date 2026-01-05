package com.slack.workspace.domain;

import com.slack.channel.domain.Channel;
import com.slack.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workspaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "workspace", fetch = FetchType.LAZY)
    private List<WorkspaceMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "workspace", fetch = FetchType.LAZY)
    private List<Channel> channels = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Builder
    public Workspace(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }

    /**
     * Determines the workspace role for a given user.
     * Returns OWNER if the user is the workspace owner, otherwise MEMBER.
     */
    public WorkspaceRole getRoleForUser(Long userId) {
        return this.owner.getId().equals(userId)
            ? WorkspaceRole.OWNER
            : WorkspaceRole.MEMBER;
    }
}

