package com.sashplatonov.earnit.kids.util;

/**
 * Represents the result of a domain operation.
 * Uses sealed interface with pattern matching for exhaustive handling.
 *
 * @param <T> the type of the success value
 */
public sealed interface OperationResult<T>
    permits OperationResult.Success, OperationResult.Failure {

    /**
     * Successful operation result carrying a value.
     */
    record Success<T>(T value) implements OperationResult<T> { }

    /**
     * Failed operation result carrying an error code and a human-readable message.
     */
    record Failure<T>(String errorCode, String message) implements OperationResult<T> { }

    /** Creates a success result with the given value. */
    static <T> OperationResult<T> success(T value) {
        return new Success<>(value);
    }

    /** Creates a failure result with the given error code and message. */
    static <T> OperationResult<T> failure(String errorCode, String message) {
        return new Failure<>(errorCode, message);
    }

    /** Creates a failure result with only a human-readable message (no error code). */
    static <T> OperationResult<T> failure(String message) {
        return new Failure<>(null, message);
    }

    /** Returns true if this result is a success. */
    default boolean isSuccess() {
        return this instanceof Success<T>;
    }
}
