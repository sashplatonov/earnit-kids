package com.sashplatonov.earnit.kids.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class TraceFilter implements ContainerRequestFilter, ContainerResponseFilter {
    public static final String TRACE_ID = "traceId";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        var trace = requestContext.getHeaderString("X-Trace-Id");
        if (trace == null || trace.isBlank()) {
            trace = UUID.randomUUID().toString();
        }
        MDC.put(TRACE_ID, trace);

        requestContext.setProperty(TRACE_ID, trace);
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        try {
            var trace = (String) requestContext.getProperty(TRACE_ID);
            if (trace != null) {
                responseContext.getHeaders().add("X-Trace-Id", trace);
            }
        } finally {
            MDC.clear();
        }
    }
}
