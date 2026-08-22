package com.sashplatonov.earnit.kids.util;

import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.shared.api.response.SimpleResponse;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.function.Function;

public final class OperationResultResponses {

    private OperationResultResponses() {
    }

    @FunctionalInterface
    public interface FailureStatusResolver {
        int resolve(OperationResult.Failure<?> failure);
    }

    @FunctionalInterface
    public interface FailureResponseResolver {
        Response resolve(OperationResult.Failure<?> failure);
    }

    public static <T> Response toResponse(OperationResult<T> result,
                                          Function<T, Response> successMapper,
                                          FailureResponseResolver failureMapper) {
        return switch (result) {
            case OperationResult.Success<T> s -> successMapper.apply(s.value());
            case OperationResult.Failure<T> f -> failureMapper.resolve(f);
        };
    }

    public static <T> Response toOk(OperationResult<T> result) {
        return toOk(result, (FailureStatusResolver) failure -> Response.Status.BAD_REQUEST.getStatusCode());
    }

    public static <T> Response toMappedOk(OperationResult<T> result, Function<T, ?> successMapper) {
        return toMappedOk(result, successMapper, failure -> Response.Status.BAD_REQUEST.getStatusCode());
    }

    public static <T> Response toMappedOk(OperationResult<T> result, Function<T, ?> successMapper,
                                          FailureStatusResolver failureStatusResolver) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.ok(successMapper.apply(s.value())).build();
            case OperationResult.Failure<T> f -> failureResponse(f, failureStatusResolver.resolve(f));
        };
    }

    public static <T> Response toMappedOk(OperationResult<T> result, Function<T, ?> successMapper,
                                          FailureStatusResolver failureStatusResolver, String failureErrorCode) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.ok(successMapper.apply(s.value())).build();
            case OperationResult.Failure<T> f -> failureResponse(
                f, failureStatusResolver.resolve(f), failureErrorCode);
        };
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

    public static <T> Response toCreated(OperationResult<T> result) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.status(Response.Status.CREATED).entity(s.value()).build();
            case OperationResult.Failure<T> f -> failureResponse(f, Response.Status.BAD_REQUEST.getStatusCode());
        };
    }

    public static <T> Response toCreated(OperationResult<T> result, String failureErrorCode) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.status(Response.Status.CREATED).entity(s.value()).build();
            case OperationResult.Failure<T> f -> failureResponse(
                f, Response.Status.BAD_REQUEST.getStatusCode(), failureErrorCode);
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

    private static Response failureResponse(OperationResult.Failure<?> failure, int status, String errorCode) {
        return Response.status(status)
            .entity(ErrorResponse.of(failure.message(), errorCode, status))
            .build();
    }
}
