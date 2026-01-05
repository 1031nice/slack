package com.slack.workspace.domain;

import com.slack.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspace_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime acceptedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (token == null) {
            token = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = InvitationStatus.PENDING;
        }
    }

    @Builder
    public WorkspaceInvitation(Workspace workspace, User inviter, String email, LocalDateTime expiresAt) {
        this.workspace = workspace;
        this.inviter = inviter;
        this.email = email;
        this.expiresAt = expiresAt;
        this.token = UUID.randomUUID().toString();
        this.status = InvitationStatus.PENDING;
    }

    public void accept() {
        if (this.status != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is not in PENDING status");
        }
        if (LocalDateTime.now().isAfter(this.expiresAt)) {
            throw new IllegalStateException("Invitation has expired");
        }
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isPending() {
        return this.status == InvitationStatus.PENDING;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }
}

