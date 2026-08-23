package com.sashplatonov.earnit.kids.family.application.invitation;

public interface ParentInvitationEmailSender {
    void send(Email email);

    record Email(String recipient, String inviteUrl, String permission, String expiresAt) { }
}
