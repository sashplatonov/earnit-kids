package com.sashplatonov.earnit.kids.util;

import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.function.Function;

// EXPLAIN: Maps OperationResult to JAX-RS Response with null-safe error codes so every resource maps failures identically.
public final class OperationResultResponses {

    private OperationResultResponses() {
    }

    // EXPLAIN: Resolves the HTTP status to use for a failure result.
    @FunctionalInterface
    public interface FailureStatusResolver {
        int resolve(OperationResult.Failure<?> failure);
    }

    public static <T> Response toOk(OperationResult<T> result) {
        return toOk(result, failure -> Response.Status.BAD_REQUEST.getStatusCode());
    }

    public static <T> Response toOk(OperationResult<T> result, FailureStatusResolver failureStatusResolver) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.ok(s.value()).build();
            case OperationResult.Failure<T> f -> failureResponse(f, failureStatusResolver.resolve(f));
        };
    }

    public static Response toVoidOk(OperationResult<Void> result) {
        return toVoidOk(result, failure -> Response.Status.BAD_REQUEST.getStatusCode());
    }

    public static Response toVoidOk(OperationResult<Void> result, FailureStatusResolver failureStatusResolver) {
        return switch (result) {
            case OperationResult.Success<Void> ignored -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f -> failureResponse(f, failureStatusResolver.resolve(f));
        };
    }

    public static <T> Response toCreated(OperationResult<T> result, URI location) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.created(location).entity(s.value()).build();
            case OperationResult.Failure<T> f -> failureResponse(f, Response.Status.BAD_REQUEST.getStatusCode());
        };
    }

    public static String errorCodeOrBadRequest(String errorCode) {
        return errorCode != null ? errorCode : "BAD_REQUEST";
    }

    private static Response failureResponse(OperationResult.Failure<?> failure, int status) {
        return Response.status(status)
            .entity(ErrorResponse.of(failure.message(), errorCodeOrBadRequest(failure.errorCode()), status))
            .build();
    }
}
