package com.sashplatonov.earnit.kids.util;

public sealed interface OperationResult<T>
    permits OperationResult.Success, OperationResult.Failure {

    record Success<T>(T value) implements OperationResult<T> { }

    record Failure<T>(String errorCode, String message) implements OperationResult<T> { }

    static <T> OperationResult<T> success(T value) {
        return new Success<>(value);
    }

    static <T> OperationResult<T> failure(String errorCode, String message) {
        return new Failure<>(errorCode, message);
    }

    static <T> OperationResult<T> failure(String message) {
        return new Failure<>(null, message);
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }
}
