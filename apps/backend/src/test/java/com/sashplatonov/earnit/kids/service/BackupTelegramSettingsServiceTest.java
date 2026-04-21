package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.BackupTelegramSettingsEntity;
import com.sashplatonov.earnit.kids.dto.request.UpdateBackupTelegramSettingsRequest;
import com.sashplatonov.earnit.kids.dto.response.BackupTelegramSettingsResponse;
import com.sashplatonov.earnit.kids.repository.BackupTelegramSettingsRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupTelegramSettingsServiceTest {

    @Mock
    BackupTelegramSettingsRepository repository;

    private BackupTelegramSettingsService service;

    @BeforeEach
    void setUp() {
        service = new BackupTelegramSettingsService(repository, false, 24);
    }

    @Test
    void getSettings_returnsFallbackWhenRepositoryEmpty() {
        when(repository.findSettings()).thenReturn(Optional.empty());

        BackupTelegramSettingsResponse response = service.getSettings();

        assertThat(response.enabled()).isFalse();
        assertThat(response.chatId()).isNull();
        assertThat(response.intervalHours()).isEqualTo(24);
        assertThat(response.hasBotToken()).isFalse();
        assertThat(response.configured()).isFalse();
    }

    @Test
    void updateSettings_preservesExistingTokenWhenNotProvided() {
        BackupTelegramSettingsEntity entity = BackupTelegramSettingsEntity.builder()
            .id(BackupTelegramSettingsEntity.DEFAULT_ID)
            .enabled(false)
            .botToken("token-1")
            .chatId("chat-1")
            .intervalHours(24)
            .build();
        when(repository.findSettings()).thenReturn(Optional.of(entity));

        OperationResult<BackupTelegramSettingsResponse> result =
            service.updateSettings(new UpdateBackupTelegramSettingsRequest(false, null, "chat-2", 48));

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(entity.getBotToken()).isEqualTo("token-1");
        assertThat(entity.getChatId()).isEqualTo("chat-2");
        assertThat(entity.getIntervalHours()).isEqualTo(48);
        verify(repository).flushChanges();
    }

    @Test
    void updateSettings_rejectsEnabledScheduleWithoutToken() {
        BackupTelegramSettingsEntity entity = BackupTelegramSettingsEntity.builder()
            .id(BackupTelegramSettingsEntity.DEFAULT_ID)
            .enabled(false)
            .botToken(null)
            .chatId(null)
            .intervalHours(24)
            .build();
        when(repository.findSettings()).thenReturn(Optional.of(entity));

        OperationResult<BackupTelegramSettingsResponse> result =
            service.updateSettings(new UpdateBackupTelegramSettingsRequest(true, null, "chat-1", 24));

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<BackupTelegramSettingsResponse>) result).message())
            .isEqualTo("Сохраните Telegram bot token");
    }
}
