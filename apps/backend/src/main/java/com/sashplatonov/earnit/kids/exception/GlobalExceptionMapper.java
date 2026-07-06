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
            log.warn(
                "REST request failed status={} method={} uri={} traceId={} referer={} userAgent={} forwardedFor={} authCookiePresent={}",
                status,
                requestMethod(),
                requestUri(),
                header("X-Trace-Id"),
                header("Referer"),
                header("User-Agent"),
                header("X-Forwarded-For"),
                hasAuthCookie(),
                exception
            );
            return webApplicationException.getResponse().hasEntity()
                ? webApplicationException.getResponse()
                : Response.status(status)
                    .entity(ErrorResponse.of(statusDetail(status), statusCode(status), status))
                    .build();
        }

        log.error(
            "Unhandled exception while processing REST request method={} uri={} traceId={} referer={} userAgent={} forwardedFor={} authCookiePresent={}",
            requestMethod(),
            requestUri(),
            header("X-Trace-Id"),
            header("Referer"),
            header("User-Agent"),
            header("X-Forwarded-For"),
            hasAuthCookie(),
            exception
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.of(BackendMessages.message("errors.internalServerError"), "INTERNAL_ERROR", 500))
            .build();
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
