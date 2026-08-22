package com.sashplatonov.earnit.kids.identity.api.resource.account;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.identity.api.request.UpdateAccountEmailRequest;
import com.sashplatonov.earnit.kids.dto.response.AccountConnectionResponse;
import com.sashplatonov.earnit.kids.identity.application.account.AccountService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountResourceTest {

    @Test
    void connection_requiresAdmin() {
        AccountService service = mock(AccountService.class);
        AccountResource resource = new AccountResource(service);
        try (Response response = resource.connection(mock(ContainerRequestContext.class))) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void connection_delegatesAuthenticatedFamilyAndEmail() {
        AccountService service = mock(AccountService.class);
        ContainerRequestContext context = adminContext();
        when(service.connection("family-1", "parent@test")).thenReturn(
            OperationResult.success(new AccountConnectionResponse("parent@test", true, false)));
        AccountResource resource = new AccountResource(service);

        try (Response response = resource.connection(context)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(service).connection("family-1", "parent@test");
    }

    @Test
    void emailOperations_delegateAuthenticatedIdentity() {
        AccountService service = mock(AccountService.class);
        when(service.changeEmail("family-1", "parent@test", "new@test"))
            .thenReturn(OperationResult.success(null));
        when(service.unlinkEmail("family-1", "parent@test")).thenReturn(OperationResult.success(null));
        AccountResource resource = new AccountResource(service);
        ContainerRequestContext context = adminContext();

        try (Response change = resource.changeEmail(context, new UpdateAccountEmailRequest("new@test"));
             Response unlink = resource.unlinkEmail(context)) {
            assertThat(change.getStatus()).isEqualTo(200);
            assertThat(unlink.getStatus()).isEqualTo(200);
        }
        verify(service).changeEmail("family-1", "parent@test", "new@test");
        verify(service).unlinkEmail("family-1", "parent@test");
    }

    private static ContainerRequestContext adminContext() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(
            new AuthContext("family-1", null, "admin", "parent@test", "csrf", false, "editor"));
        return context;
    }
}
