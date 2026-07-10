package com.sashplatonov.earnit.kids.exception;

import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.config.observability.TraceFilter;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Provider
@Slf4j
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    @Context
    Request request;

    @Context
    UriInfo uriInfo;

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webApplicationException) {
            int status = webApplicationException.getResponse().getStatus();
            logWebApplicationFailure(status, exception);
            return webApplicationException.getResponse().hasEntity()
                ? webApplicationException.getResponse()
                : Response.status(status)
                    .entity(ErrorResponse.of(statusDetail(status), statusCode(status), status, traceId()))
                    .build();
        }

        log.error(
            "Unhandled exception while processing REST request {}",
            requestContext(),
            exception
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.of(
                BackendMessages.message("errors.internalServerError"),
                "INTERNAL_ERROR",
                500,
                traceId()
            ))
            .build();
    }

    void logWebApplicationFailure(int status, Throwable exception) {
        if (shouldLogAsError(status)) {
            log.error("REST request failed status={} {}", status, requestContext(), exception);
            return;
        }

        if (shouldLogAsInfo(status)) {
            log.info("REST request failed status={} {}", status, requestContext());
            return;
        }

        log.warn("REST request failed status={} {}", status, requestContext());
    }

    private String requestMethod() {
        return mdcOrDefault(TraceFilter.REQUEST_METHOD, request == null ? "-" : request.getMethod());
    }

    private String requestUri() {
        return mdcOrDefault(
            TraceFilter.REQUEST_PATH,
            uriInfo == null || uriInfo.getPath() == null || uriInfo.getPath().isBlank()
                ? "-"
                : "/" + uriInfo.getPath()
        );
    }

    private String requestQuery() {
        return mdcOrDefault(TraceFilter.REQUEST_QUERY, "-");
    }

    private boolean hasAuthCookie() {
        if (headers == null) {
            return false;
        }
        String cookie = headers.getHeaderString(HttpHeaders.COOKIE);
        return cookie != null && cookie.contains("app_auth=");
    }

    private String requestContext() {
        return "method=" + requestMethod()
            + " uri=" + requestUri()
            + " query=" + requestQuery()
            + " traceId=" + traceId()
            + " role=" + mdcOrDefault(AuthFilter.MDC_ROLE, "-")
            + " familyId=" + mdcOrDefault(AuthFilter.MDC_FAMILY_ID, "-")
            + " childId=" + mdcOrDefault(AuthFilter.MDC_CHILD_ID, "-")
            + " permission=" + mdcOrDefault(AuthFilter.MDC_PERMISSION, "-")
            + " referer=" + header("Referer")
            + " userAgent=" + header("User-Agent")
            + " forwardedFor=" + header("X-Forwarded-For")
            + " authCookiePresent=" + hasAuthCookie();
    }

    private String header(String name) {
        if (headers == null) {
            return "-";
        }
        String value = headers.getHeaderString(name);
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 256 ? value.substring(0, 256) + "..." : value;
    }

    private String mdcOrDefault(String key, String fallback) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String traceId() {
        return mdcOrDefault(TraceFilter.TRACE_ID, header("X-Trace-Id"));
    }

    boolean shouldLogAsError(int status) {
        return status >= 500;
    }

    boolean shouldLogAsInfo(int status) {
        return status == 404;
    }

    private String statusDetail(int status) {
        return switch (status) {
            case 404 -> "Resource not found";
            case 405 -> "Method not allowed";
            default -> Response.Status.fromStatusCode(status) != null
                ? Response.Status.fromStatusCode(status).getReasonPhrase()
                : "Request failed";
        };
    }

    private String statusCode(int status) {
        return switch (status) {
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            default -> "REQUEST_FAILED";
        };
    }
}
