package com.slack.service.notification;

import com.slack.domain.workspace.WorkspaceInvitation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Development-only notifier that logs invitation details instead of sending emails.
 */
@Slf4j
@Service
@Profile("dev")
public class LoggingInvitationNotifier implements InvitationNotifier {

    @Override
    public void sendInvitation(WorkspaceInvitation invitation) {
        log.info("📧 [DEV] Invitation email (not sent):");
        log.info("  To: {}", invitation.getEmail());
        log.info("  Workspace: {}", invitation.getWorkspace().getName());
        log.info("  Inviter: {}", invitation.getInviter().getName());
        log.info("  Token: {}", invitation.getToken());
        log.info("  Expires: {}", invitation.getExpiresAt());
        log.info("  Invitation link: http://localhost:3000/accept-invite?token={}", invitation.getToken());
    }
}
