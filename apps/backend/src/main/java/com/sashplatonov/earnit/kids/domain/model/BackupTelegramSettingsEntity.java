package com.sashplatonov.earnit.kids.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "backup_telegram_settings")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BackupTelegramSettingsEntity extends CreatedAtEntity {

    public static final String DEFAULT_ID = "default";

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private String id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "bot_token", length = 512)
    private String botToken;

    @Column(name = "chat_id", length = 255)
    private String chatId;

    @Column(name = "interval_hours", nullable = false)
    private int intervalHours;

    @Column(name = "backup_retention_count", nullable = false)
    private int backupRetentionCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
