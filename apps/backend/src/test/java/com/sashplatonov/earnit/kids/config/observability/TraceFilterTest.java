package com.sashplatonov.earnit.kids.config.observability;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceFilterTest {

    private final TraceFilter filter = new TraceFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void filter_acceptsTraceparent_populatesMdcAndAddsTraceHeaders() throws Exception {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        Map<String, Object> properties = new HashMap<>();

        when(request.getHeaderString(TraceFilter.TRACEPARENT))
            .thenReturn("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        when(request.getMethod()).thenReturn("GET");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("api/data");
        when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost/api/data?foo=bar"));
        doAnswer(invocation -> {
            properties.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(request).setProperty(anyString(), any());
        when(request.getProperty(anyString())).thenAnswer(invocation -> properties.get(invocation.getArgument(0)));
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request);

        assertThat(MDC.get(TraceFilter.TRACE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(MDC.get(TraceFilter.REQUEST_METHOD)).isEqualTo("GET");
        assertThat(MDC.get(TraceFilter.REQUEST_PATH)).isEqualTo("/api/data");
        assertThat(MDC.get(TraceFilter.REQUEST_QUERY)).isEqualTo("foo=bar");

        filter.filter(request, response);

        assertThat(headers.getFirst("X-Trace-Id"))
            .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(headers.getFirst(TraceFilter.TRACEPARENT).toString())
            .matches("00-4bf92f3577b34da6a3ce929d0e0e4736-[0-9a-f]{16}-01");
        assertThat(MDC.get(TraceFilter.TRACE_ID)).isNull();
        assertThat(MDC.get(TraceFilter.REQUEST_METHOD)).isNull();
        assertThat(MDC.get(TraceFilter.REQUEST_PATH)).isNull();
        assertThat(MDC.get(TraceFilter.REQUEST_QUERY)).isNull();
    }

    @Test
    void filter_legacyTraceId_keepsFallbackAndSkipsTraceparent() throws Exception {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        Map<String, Object> properties = new HashMap<>();

        when(request.getHeaderString(TraceFilter.TRACEPARENT)).thenReturn(null);
        when(request.getHeaderString("X-Trace-Id")).thenReturn("legacy-trace-123");
        when(request.getMethod()).thenReturn("POST");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("api/data");
        when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost/api/data"));
        doAnswer(invocation -> {
            properties.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(request).setProperty(anyString(), any());
        when(request.getProperty(anyString())).thenAnswer(invocation -> properties.get(invocation.getArgument(0)));
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request);
        filter.filter(request, response);

        assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("legacy-trace-123");
        assertThat(headers.containsKey(TraceFilter.TRACEPARENT)).isFalse();
        assertThat(MDC.get(TraceFilter.TRACE_ID)).isNull();
    }

    @Test
    void filter_normalizesPathThatAlreadyStartsWithSlash() throws Exception {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);

        when(request.getMethod()).thenReturn("GET");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("//api/analytics");
        when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost/api/analytics"));

        filter.filter(request);

        assertThat(MDC.get(TraceFilter.REQUEST_PATH)).isEqualTo("/api/analytics");
    }
}
