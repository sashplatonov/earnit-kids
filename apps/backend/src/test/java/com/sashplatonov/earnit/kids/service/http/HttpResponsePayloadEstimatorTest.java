package com.sashplatonov.earnit.kids.service.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpResponsePayloadEstimatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void estimate_usesContentLengthWhenAvailable() {
        HttpResponsePayloadEstimator estimator = new HttpResponsePayloadEstimator(objectMapper, true, 256);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(HttpHeaders.CONTENT_LENGTH, "42");

        when(request.getMethod()).thenReturn("GET");
        when(response.getHeaders()).thenReturn(headers);
        when(response.getEntity()).thenReturn(Map.of("hello", "world"));

        long result = estimator.estimate(request, response, "/api/data");

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void estimate_serializesSmallJsonEntityWhenNeeded() throws Exception {
        HttpResponsePayloadEstimator estimator = new HttpResponsePayloadEstimator(objectMapper, true, 256);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        when(request.getMethod()).thenReturn("GET");
        when(response.getHeaders()).thenReturn(headers);
        when(response.getEntity()).thenReturn(Map.of("hello", "world"));
        when(response.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);

        long result = estimator.estimate(request, response, "/api/data");

        assertThat(result).isGreaterThan(0L);
    }

    @Test
    void estimate_skipsLargeCollections() {
        HttpResponsePayloadEstimator estimator = new HttpResponsePayloadEstimator(objectMapper, true, 2);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        when(request.getMethod()).thenReturn("GET");
        when(response.getHeaders()).thenReturn(headers);
        when(response.getEntity()).thenReturn(List.of(1, 2, 3));
        when(response.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);

        long result = estimator.estimate(request, response, "/api/data");

        assertThat(result).isEqualTo(-1L);
    }

    @Test
    void estimate_handlesStringAndByteArrayWithoutJsonSerialization() {
        HttpResponsePayloadEstimator estimator = new HttpResponsePayloadEstimator(objectMapper, true, 256);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        when(request.getMethod()).thenReturn("GET");
        when(response.getHeaders()).thenReturn(headers);
        when(response.getEntity()).thenReturn("hello");

        long stringResult = estimator.estimate(request, response, "/api/data");

        when(response.getEntity()).thenReturn(new byte[] {1, 2, 3});
        long bytesResult = estimator.estimate(request, response, "/api/data");

        assertThat(stringResult).isEqualTo(5L);
        assertThat(bytesResult).isEqualTo(3L);
    }
}
