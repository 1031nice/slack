package com.slack.service.notification;

import com.slack.domain.workspace.WorkspaceInvitation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Non-production notifier that logs invitation details instead of sending emails.
 * Used in all environments except production (dev, test, 9000, 9001, 9002, etc.)
 */
@Slf4j
@Service
@Profile("!prod")
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
