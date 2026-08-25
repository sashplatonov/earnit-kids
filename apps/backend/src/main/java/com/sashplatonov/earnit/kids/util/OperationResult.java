package com.sashplatonov.earnit.kids.util;

import java.util.Map;

public sealed interface OperationResult<T>
    permits OperationResult.Success, OperationResult.Failure {

  record Success<T>(T value) implements OperationResult<T> {}

  record Failure<T>(String errorCode, Map<String, Object> params, String message) implements OperationResult<T> {
    public Failure {
      params = params == null || params.isEmpty() ? Map.of() : Map.copyOf(params);
    }

    public Failure(String errorCode, String message) {
      this(errorCode, Map.of(), message);
    }
  }

  static <T> OperationResult<T> success(T value) {
    return new Success<>(value);
  }

  static <T> OperationResult<T> failure(String errorCode, String message) {
    return new Failure<>(errorCode, message);
  }

  static <T> OperationResult<T> failure(String errorCode, Map<String, Object> params, String message) {
    return new Failure<>(errorCode, params, message);
  }

  static <T> OperationResult<T> failure(String message) {
    return new Failure<>(null, message);
  }

  default boolean isSuccess() {
    return this instanceof Success<T>;
  }

  default boolean isFailure() {
    return this instanceof Failure<T>;
  }

  // EXPLAIN: Reconstructs this failure as a value-less OperationResult<R> so callers can
  // EXPLAIN: early-return a failure of any payload type without an unchecked cast.
  default <R> OperationResult<R> asFailure() {
    if (this instanceof Failure<T> f) {
      return OperationResult.failure(f.errorCode(), f.params(), f.message());
    }
    throw new IllegalStateException("asFailure() called on a success result");
  }
}
