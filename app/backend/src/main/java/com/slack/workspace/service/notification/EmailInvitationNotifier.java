package com.slack.workspace.service.notification;

import com.slack.workspace.domain.WorkspaceInvitation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Production notifier that sends actual invitation emails.
 */
@Slf4j
@Service
@Profile("prod")
public class EmailInvitationNotifier implements InvitationNotifier {

    @Override
    public void sendInvitation(WorkspaceInvitation invitation) {
        // TODO: Implement actual email sending
        // Options:
        // - Spring Boot Mail (JavaMailSender)
        // - SendGrid API
        // - AWS SES
        // - Mailgun

        log.info("Sending invitation email to {} for workspace {}",
                invitation.getEmail(),
                invitation.getWorkspace().getName());

        throw new UnsupportedOperationException("Email sending not yet implemented");
    }
}
