package com.sashplatonov.earnit.kids.exception;

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
                    .entity(ErrorResponse.of(statusDetail(status), statusCode(status), status))
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
                500
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
        return request == null ? "-" : request.getMethod();
    }

    private String requestUri() {
        return uriInfo == null || uriInfo.getRequestUri() == null ? "-" : uriInfo.getRequestUri().toString();
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
            + " traceId=" + header("X-Trace-Id")
            + " referer=" + header("Referer")
            + " userAgent=" + header("User-Agent")
            + " forwardedFor=" + header("X-Forwarded-For")
            + " authCookiePresent=" + hasAuthCookie();
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
