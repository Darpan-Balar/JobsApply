package com.jobsapply.spi.common;

import java.time.Instant;

public record HealthReport(Status status, String detail, Instant checkedAt) {

    public enum Status {
        UP,
        DEGRADED,
        DOWN,
        UNKNOWN
    }
}
