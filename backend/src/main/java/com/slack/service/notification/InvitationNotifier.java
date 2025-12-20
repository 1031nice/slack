package com.slack.service.notification;

import com.slack.domain.workspace.WorkspaceInvitation;

/**
 * Sends workspace invitation notifications.
 */
public interface InvitationNotifier {
    void sendInvitation(WorkspaceInvitation invitation);
}
