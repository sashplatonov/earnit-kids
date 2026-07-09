package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceImplTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");

    @Mock FamilyRepository familyRepository;
    @Mock HistoryRepository historyRepository;
    @Mock TaskRepository taskRepository;
    @Mock ShopItemRepository shopItemRepository;

    private SimpleMeterRegistry meterRegistry;
    private BackendKpiMetrics backendKpiMetrics;
    private AnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        backendKpiMetrics = new BackendKpiMetrics(meterRegistry);
        service = new AnalyticsServiceImpl(
            familyRepository,
            historyRepository,
            taskRepository,
            shopItemRepository,
            TestConfigFactory.timeProvider(FIXED_NOW),
            backendKpiMetrics
        );
    }

    @Test
    void getAnalyticsData_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());

        OperationResult<AnalyticsResponse> result = service.getAnalyticsData("missing", null, "month");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(meterRegistry.find("earnit.backend.service.operation.count")
            .tags("service", "analytics", "operation", "get_data", "outcome", "failure")
            .counter()).isNotNull();
        assertThat(meterRegistry.find("earnit.backend.service.operation.duration")
            .tags("service", "analytics", "operation", "get_data", "outcome", "failure")
            .timer()).isNotNull();
    }

    @Test
    void getAnalyticsData_validHistory_buildsTypedAnalyticsResponse() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));

        Instant now = FIXED_NOW;
        List<HistoryEntryEntity> current = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type(HistoryEntryType.earn).amount(5)
                .relatedId(1001L).description("Task 1").createdAt(now.minusSeconds(3600)).build(),
            HistoryEntryEntity.builder().familyId(1).childId(10).type(HistoryEntryType.spend).amount(3)
                .relatedId(2001L).description("Item 1").createdAt(now.minusSeconds(1800)).build()
        );
        List<HistoryEntryEntity> previous = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type(HistoryEntryType.earn).amount(2)
                .createdAt(now.minusSeconds(60 * 60 * 24 * 40L)).build()
        );
        List<HistoryEntryEntity> monthly = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type(HistoryEntryType.earn).amount(4)
                .relatedId(1001L).createdAt(now.minusSeconds(60 * 60 * 24)).build()
        );

        doReturn(current).doReturn(previous).doReturn(monthly)
            .when(historyRepository).list(anyString(), any(Object[].class));
        when(historyRepository.summarizePeriod(anyInt(), any(), any(), any())).thenReturn(new int[]{5, 3});
        List<Object[]> topTasks = List.<Object[]>of(new Object[]{1001L, 5, 1});
        List<Object[]> topItems = List.<Object[]>of(new Object[]{2001L, 3, 1});
        when(historyRepository.topTasksInPeriod(anyInt(), any(), any(), any())).thenReturn(topTasks);
        when(historyRepository.topItemsInPeriod(anyInt(), any(), any(), any())).thenReturn(topItems);
        when(historyRepository.dailyTrendInPeriod(anyInt(), any(), any(), any())).thenReturn(List.of(
            new Object[]{java.sql.Date.valueOf("2026-04-15"), "earn", 5},
            new Object[]{java.sql.Date.valueOf("2026-04-15"), "spend", 3}
        ));

        List<TaskEntity> tasks = List.of(
            TaskEntity.builder().familyId(1).childId(10).taskId(1001L).name("Read").coins(5).build(),
            TaskEntity.builder().familyId(1).childId(10).taskId(1002L).name("Clean").coins(7).build()
        );
        doReturn(tasks).doReturn(tasks)
            .when(taskRepository).list(anyString(), any(Object[].class));

        List<ShopItemEntity> items = List.of(
            ShopItemEntity.builder().familyId(1).childId(10).itemId(2001L).name("Toy").price(3).build()
        );
        doReturn(items).when(shopItemRepository).list(anyString(), any(Object[].class));

        OperationResult<AnalyticsResponse> result = service.getAnalyticsData("fam-1", null, "month");

        AnalyticsResponse payload = successValue(result);

        assertThat(payload.summary()).isNotNull();
        assertThat(payload.summary().totalEarned()).isGreaterThanOrEqualTo(0);
        assertThat(payload.summary().totalSpent()).isGreaterThanOrEqualTo(0);
        assertThat(payload.topTasks()).isNotNull();
        assertThat(payload.trends()).isNotNull();
        assertThat(payload.recommendations()).hasSizeLessThanOrEqualTo(3);
        assertThat(meterRegistry.find("earnit.backend.service.operation.count")
            .tags("service", "analytics", "operation", "get_data", "outcome", "success")
            .counter()).isNotNull();
    }

    @Test
    void getAnalyticsData_missingAggregateRows_fallsBackToZeroSummary() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(historyRepository.summarizePeriod(anyInt(), any(), any(), any())).thenReturn(null);
        doReturn(List.of()).when(taskRepository).list(anyString(), any(Object[].class));
        doReturn(List.of()).when(shopItemRepository).list(anyString(), any(Object[].class));

        OperationResult<AnalyticsResponse> result = service.getAnalyticsData("fam-1", 10, "month");

        AnalyticsResponse payload = successValue(result);
        assertThat(payload.summary().totalEarned()).isZero();
        assertThat(payload.summary().totalSpent()).isZero();
        assertThat(payload.comparison().totalEarned()).isZero();
        assertThat(payload.comparison().totalSpent()).isZero();
    }

    @Test
    void getAnalyticsData_reusesCacheUntilTargetFamilyInvalidation() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getDbId("fam-2")).thenReturn(Optional.of(2));
        when(historyRepository.summarizePeriod(anyInt(), any(), any(), any())).thenReturn(new int[]{5, 3});
        when(historyRepository.topTasksInPeriod(anyInt(), any(), any(), any())).thenReturn(List.of());
        when(historyRepository.topItemsInPeriod(anyInt(), any(), any(), any())).thenReturn(List.of());
        when(historyRepository.dailyTrendInPeriod(anyInt(), any(), any(), any())).thenReturn(List.of());
        doReturn(List.of()).when(taskRepository).list(anyString(), any(Object[].class));
        doReturn(List.of()).when(shopItemRepository).list(anyString(), any(Object[].class));

        OperationResult<AnalyticsResponse> first = service.getAnalyticsData("fam-1", null, "month");
        OperationResult<AnalyticsResponse> second = service.getAnalyticsData("fam-1", null, "month");
        OperationResult<AnalyticsResponse> otherFamily = service.getAnalyticsData("fam-2", null, "month");

        assertThat(first).isInstanceOf(OperationResult.Success.class);
        assertThat(second).isInstanceOf(OperationResult.Success.class);
        assertThat(otherFamily).isInstanceOf(OperationResult.Success.class);
        verify(historyRepository, times(4)).summarizePeriod(anyInt(), any(), any(), any());
        verify(historyRepository, times(2)).topTasksInPeriod(anyInt(), any(), any(), any());
        verify(historyRepository, times(2)).topItemsInPeriod(anyInt(), any(), any(), any());
        verify(historyRepository, times(2)).dailyTrendInPeriod(anyInt(), any(), any(), any());
        verify(taskRepository, times(4)).list(anyString(), any(Object[].class));
        verify(shopItemRepository, times(2)).list(anyString(), any(Object[].class));

        service.invalidateCache("fam-1");
        service.getAnalyticsData("fam-1", null, "month");
        service.getAnalyticsData("fam-2", null, "month");
        verify(historyRepository, times(6)).summarizePeriod(anyInt(), any(), any(), any());
    }

    private static <T> T successValue(OperationResult<T> result) {
        assertThat(result).isInstanceOf(OperationResult.Success.class);
        return ((OperationResult.Success<T>) result).value();
    }
}
