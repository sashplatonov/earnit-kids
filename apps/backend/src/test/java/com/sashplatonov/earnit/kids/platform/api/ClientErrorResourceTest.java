package com.sashplatonov.earnit.kids.platform.api;

import com.sashplatonov.earnit.kids.resource.common.ClientErrorResource;
import com.sashplatonov.earnit.kids.resource.common.ClientErrorMessage;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ClientErrorResourceTest {

    private final ClientErrorResource resource = new ClientErrorResource();
    private final ContainerRequestContext request = authenticatedRequest();

    @Test
    void reportClientError_acceptsPayload() {
        Response response = resource.reportClientError(
            new ClientErrorMessage("web.proxy_failure", "/api/data", 503, "upstream_unavailable", "trace-1", "TypeError"),
            request);

        assertThat(response.getStatus()).isEqualTo(202);
    }

    @Test
    void reportClientError_rejectsUnknownCodeAndUnauthenticatedRequest() {
        ClientErrorMessage message = new ClientErrorMessage("client.anything", "/api/data", 500, "failure", "trace", "Error");
        assertThat(resource.reportClientError(message, request).getStatus()).isEqualTo(400);
        assertThat(resource.reportClientError(message, Mockito.mock(ContainerRequestContext.class)).getStatus()).isEqualTo(400);
    }

    private ContainerRequestContext authenticatedRequest() {
        ContainerRequestContext context = Mockito.mock(ContainerRequestContext.class);
        Mockito.when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(new Object());
        return context;
    }
}
