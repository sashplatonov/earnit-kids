package com.sashplatonov.earnit.kids.admin.api.resource;

import com.sashplatonov.earnit.kids.admin.application.AdminCoinEconomyService;
import com.sashplatonov.earnit.kids.admin.application.AdminTaskEconomyService;
import com.sashplatonov.earnit.kids.admin.application.AdminTrendsService;
import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTrendsResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAnalyticsResourceCoverageTest {
    private final ContainerRequestContext context = mock(ContainerRequestContext.class);

    @Test
    void invalidPeriodReturnsBadRequestForAllAnalyticsResources() {
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(admin());
        try (Response a = new AdminTrendsResource(mock(AdminTrendsService.class)).getTrends(context, "bad");
             Response b = new AdminCoinEconomyResource(mock(AdminCoinEconomyService.class)).getCoinEconomy(context, "bad");
             Response c = new AdminTaskEconomyResource(mock(AdminTaskEconomyService.class)).getTaskEconomy(context, "bad")) {
            assertThat(a.getStatus()).isEqualTo(400);
            assertThat(b.getStatus()).isEqualTo(400);
            assertThat(c.getStatus()).isEqualTo(400);
        }
    }

    @Test
    void validPeriodReturnsServiceResponses() {
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(admin());
        AdminTrendsService trends = mock(AdminTrendsService.class);
        AdminCoinEconomyService coins = mock(AdminCoinEconomyService.class);
        AdminTaskEconomyService tasks = mock(AdminTaskEconomyService.class);
        when(trends.getTrends(any())).thenReturn(mock(AdminTrendsResponse.class));
        when(coins.getCoinEconomy(any())).thenReturn(mock(AdminCoinEconomyResponse.class));
        when(tasks.getTaskEconomy(any())).thenReturn(mock(AdminTasksResponse.class));
        try (Response a = new AdminTrendsResource(trends).getTrends(context, "30d");
             Response b = new AdminCoinEconomyResource(coins).getCoinEconomy(context, "30d");
             Response c = new AdminTaskEconomyResource(tasks).getTaskEconomy(context, "30d")) {
            assertThat(a.getStatus()).isEqualTo(200);
            assertThat(b.getStatus()).isEqualTo(200);
            assertThat(c.getStatus()).isEqualTo(200);
        }
    }

    private static AuthContext admin() {
        return new AuthContext("family-1", null, "admin", "admin@test", "csrf", false, "family_admin");
    }
}
