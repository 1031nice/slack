package com.slack.workspace.service.notification;

import com.slack.workspace.domain.WorkspaceInvitation;

/**
 * Sends workspace invitation notifications.
 */
public interface InvitationNotifier {
    void sendInvitation(WorkspaceInvitation invitation);
}
