package com.sashplatonov.earnit.kids.config.observability;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.MDC;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class TraceFilter implements ContainerRequestFilter, ContainerResponseFilter {
    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_METHOD = "requestMethod";
    public static final String REQUEST_PATH = "requestPath";
    public static final String REQUEST_QUERY = "requestQuery";
    public static final String TRACEPARENT = "traceparent";
    private static final String TRACEPARENT_VERSION = "00";
    private static final String TRACEPARENT_ACCEPTED_PROPERTY = "tracefilter.traceparentAccepted";
    private static final String SPAN_ID_PROPERTY = "tracefilter.spanId";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        MDC.clear();
        String trace = resolveTraceId(requestContext);
        String spanId = randomSpanId();
        boolean traceparentAccepted = isTraceparent(requestContext.getHeaderString(TRACEPARENT));

        MDC.put(TRACE_ID, trace);
        MDC.put(REQUEST_METHOD, requestContext.getMethod());

        var uriInfo = requestContext.getUriInfo();
        String path = RequestPathNormalizer.normalize(uriInfo == null ? null : uriInfo.getPath());
        String query = uriInfo == null || uriInfo.getRequestUri() == null
            ? null
            : uriInfo.getRequestUri().getRawQuery();
        MDC.put(REQUEST_PATH, path);
        MDC.put(REQUEST_QUERY, query == null || query.isBlank() ? "-" : query);

        requestContext.setProperty(TRACE_ID, trace);
        requestContext.setProperty(TRACEPARENT_ACCEPTED_PROPERTY, traceparentAccepted);
        requestContext.setProperty(SPAN_ID_PROPERTY, spanId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        try {
            var trace = (String) requestContext.getProperty(TRACE_ID);
            if (trace != null) {
                responseContext.getHeaders().add("X-Trace-Id", trace);
                if (Boolean.TRUE.equals(requestContext.getProperty(TRACEPARENT_ACCEPTED_PROPERTY))) {
                    responseContext.getHeaders().add(
                        TRACEPARENT,
                        TRACEPARENT_VERSION + "-" + trace + "-" + responseSpanId(requestContext) + "-01"
                    );
                }
            }
        } finally {
            MDC.clear();
        }
    }

    private String resolveTraceId(ContainerRequestContext requestContext) {
        String traceparent = requestContext.getHeaderString(TRACEPARENT);
        if (traceparent != null && !traceparent.isBlank()) {
            String traceId = parseTraceparentTraceId(traceparent.trim());
            if (traceId != null) {
                return traceId;
            }
        }

        String trace = requestContext.getHeaderString("X-Trace-Id");
        if (trace == null || trace.isBlank()) {
            return randomHexId(32);
        }
        return trace.trim();
    }

    private boolean isTraceparent(String traceparent) {
        return traceparent != null && parseTraceparentTraceId(traceparent.trim()) != null;
    }

    private String parseTraceparentTraceId(String traceparent) {
        if (traceparent.length() != 55
            || traceparent.charAt(2) != '-'
            || traceparent.charAt(35) != '-'
            || traceparent.charAt(52) != '-'
            || traceparent.charAt(0) != '0'
            || traceparent.charAt(1) != '0') {
            return null;
        }
        String traceId = traceparent.substring(3, 35);
        String spanId = traceparent.substring(36, 52);
        String flags = traceparent.substring(53, 55);
        if (!isHex(traceId, 32) || !isHex(spanId, 16) || !isHex(flags, 2)) {
            return null;
        }
        return traceId.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isHex(String value, int expectedLength) {
        if (value == null || value.length() != expectedLength) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private String randomSpanId() {
        return randomHexId(16);
    }

    private String responseSpanId(ContainerRequestContext requestContext) {
        Object spanId = requestContext.getProperty(SPAN_ID_PROPERTY);
        return spanId instanceof String value && !value.isBlank() ? value : randomSpanId();
    }

    private String randomHexId(int length) {
        char[] chars = new char[length];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        fillHex(chars, 0, length / 2, random.nextLong());
        fillHex(chars, length / 2, length - (length / 2), random.nextLong());
        return new String(chars);
    }

    private void fillHex(char[] target, int offset, int count, long seed) {
        for (int index = 0; index < count; index++) {
            int shift = 60 - (index * 4);
            int nibble = (int) ((seed >>> shift) & 0x0f);
            target[offset + index] = HEX[nibble];
        }
    }
}
