package com.sashplatonov.earnit.kids.family.application.invitation;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DisabledParentInvitationEmailSender implements ParentInvitationEmailSender {
    private static final Logger LOG = Logger.getLogger(DisabledParentInvitationEmailSender.class);

    @ConfigProperty(name = "PARENT_INVITATION_MAIL_PROVIDER", defaultValue = "disabled")
    String provider;

    @Override
    public void send(Email email) {
        if (!"disabled".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("Unsupported parent invitation mail provider: " + provider);
        }
        // EXPLAIN: Development keeps delivery disabled while retaining the same adapter contract.
        LOG.infof("Parent invitation delivery disabled: recipient=%s, permission=%s, expiresAt=%s",
            email.recipient(), email.permission(), email.expiresAt());
    }
}
