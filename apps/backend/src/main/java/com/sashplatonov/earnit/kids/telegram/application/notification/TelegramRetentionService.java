package com.sashplatonov.earnit.kids.telegram.application.notification;

import com.sashplatonov.earnit.kids.telegram.config.TelegramRetentionConfig;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.outbox.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramCallbackActionRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramChildInvitationRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramDeliveryRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramSecurityAuditEventRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramWebhookUpdateRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class TelegramRetentionService {
    private static final Logger LOG = Logger.getLogger(TelegramRetentionService.class);

    private TelegramChildInvitationRepository invitations;
    private TelegramCallbackActionRepository callbacks;
    private TelegramWebhookUpdateRepository webhookUpdates;
    private TelegramDeliveryRepository deliveries;
    private ApplicationOutboxEventRepository outboxEvents;
    private TelegramSecurityAuditEventRepository auditEvents;
    private TelegramRetentionConfig config;

    TelegramRetentionService() {
    }

    @Inject
    TelegramRetentionService(TelegramChildInvitationRepository invitations,
                             TelegramCallbackActionRepository callbacks,
                             TelegramWebhookUpdateRepository webhookUpdates,
                             TelegramDeliveryRepository deliveries,
                             ApplicationOutboxEventRepository outboxEvents,
                             TelegramSecurityAuditEventRepository auditEvents,
                             TelegramRetentionConfig config) {
        this.invitations = invitations;
        this.callbacks = callbacks;
        this.webhookUpdates = webhookUpdates;
        this.deliveries = deliveries;
        this.outboxEvents = outboxEvents;
        this.auditEvents = auditEvents;
        this.config = config;
    }

    @Scheduled(
        every = "{app.telegram.retention-poll-interval}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    @Transactional
    void scheduled() {
        if (config.retentionEnabled()) {
            cleanup(Instant.now(), false);
        }
    }

    public TelegramRetentionReport dryRun(Instant now) {
        return cleanup(now, true);
    }

    @Transactional
    public TelegramRetentionReport cleanup(Instant now, boolean dryRun) {
        int batchSize = positive(config.retentionBatchSize(), "retention batch size");
        int inviteDays = positive(config.inviteRetentionDays(), "invite retention days");
        int callbackDays = positive(config.callbackRetentionDays(), "callback retention days");
        int webhookDays = positive(
            config.webhookUpdateRetentionDays(), "webhook update retention days");
        int deliveryDays = positive(config.deliveryRetentionDays(), "delivery retention days");
        int outboxDays = positive(config.outboxRetentionDays(), "outbox retention days");
        int auditDays = positive(config.auditRetentionDays(), "audit retention days");
        int invitationCount = clean(invitations, now, inviteDays, batchSize, dryRun);
        int callbackCount = clean(callbacks, now, callbackDays, batchSize, dryRun);
        int webhookCount = clean(webhookUpdates, now, webhookDays, batchSize, dryRun);
        int deliveryCount = clean(deliveries, now, deliveryDays, batchSize, dryRun);
        int outboxCount = clean(outboxEvents, now, outboxDays, batchSize, dryRun);
        int auditCount = clean(auditEvents, now, auditDays, batchSize, dryRun);
        var report = new TelegramRetentionReport(invitationCount, callbackCount, webhookCount,
                deliveryCount, outboxCount, auditCount);
        LOG.infof("Telegram retention %s: invitations=%d callbacks=%d webhookUpdates=%d "
                + "deliveries=%d outboxEvents=%d auditEvents=%d",
                dryRun ? "dry-run" : "cleanup", report.invitations(), report.callbacks(), report.webhookUpdates(),
                report.deliveries(), report.outboxEvents(), report.auditEvents());
        return report;
    }

    private int clean(TelegramChildInvitationRepository repository, Instant now, int retentionDays,
                      int batchSize, boolean dryRun) {
        return dryRun ? toInt(repository.countEligible(cutoff(now, retentionDays)))
                : repository.deleteEligible(cutoff(now, retentionDays), batchSize);
    }

    private int clean(TelegramCallbackActionRepository repository, Instant now, int retentionDays,
                      int batchSize, boolean dryRun) {
        return dryRun ? toInt(repository.countEligible(cutoff(now, retentionDays)))
                : repository.deleteEligible(cutoff(now, retentionDays), batchSize);
    }

    private int clean(TelegramWebhookUpdateRepository repository, Instant now, int retentionDays,
                      int batchSize, boolean dryRun) {
        return dryRun ? toInt(repository.countEligible(cutoff(now, retentionDays)))
                : repository.deleteEligible(cutoff(now, retentionDays), batchSize);
    }

    private int clean(TelegramDeliveryRepository repository, Instant now, int retentionDays,
                      int batchSize, boolean dryRun) {
        return dryRun ? toInt(repository.countEligible(cutoff(now, retentionDays)))
                : repository.deleteEligible(cutoff(now, retentionDays), batchSize);
    }

    private int clean(ApplicationOutboxEventRepository repository, Instant now, int retentionDays,
                      int batchSize, boolean dryRun) {
        return dryRun ? toInt(repository.countEligible(cutoff(now, retentionDays)))
                : repository.deleteEligible(cutoff(now, retentionDays), batchSize);
    }

    private int clean(TelegramSecurityAuditEventRepository repository, Instant now, int retentionDays,
                      int batchSize, boolean dryRun) {
        return dryRun ? toInt(repository.countEligible(cutoff(now, retentionDays)))
                : repository.deleteEligible(cutoff(now, retentionDays), batchSize);
    }

    private Instant cutoff(Instant now, int retentionDays) {
        return now.minus(retentionDays, ChronoUnit.DAYS);
    }

    private int positive(int value, String setting) {
        if (value < 1) {
            throw new IllegalArgumentException(setting + " must be positive");
        }
        return value;
    }

    private int toInt(long count) {
        return (int) Math.min(Integer.MAX_VALUE, count);
    }
}
