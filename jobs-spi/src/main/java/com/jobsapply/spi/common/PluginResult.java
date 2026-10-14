package com.jobsapply.spi.common;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of a plugin operation. Expected failures are values, not exceptions, so that callers handle them
 * explicitly (exhaustive {@code switch} over this sealed type).
 */
public sealed interface PluginResult<T> {

    record Ok<T>(T value) implements PluginResult<T> {
        public Ok {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * @param retryAfter provider-requested wait before retrying, or {@code null} if none was given
     */
    record Failed<T>(FailureKind kind, String message, Duration retryAfter) implements PluginResult<T> {
        public Failed {
            Objects.requireNonNull(kind, "kind");
        }
    }

    record Unsupported<T>(Capability capability) implements PluginResult<T> {}

    static <T> PluginResult<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> PluginResult<T> failed(FailureKind kind, String message) {
        return new Failed<>(kind, message, null);
    }

    static <T> PluginResult<T> unsupported(Capability capability) {
        return new Unsupported<>(capability);
    }
}
