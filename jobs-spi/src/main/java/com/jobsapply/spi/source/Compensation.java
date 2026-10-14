package com.jobsapply.spi.source;

import com.jobsapply.spi.common.CompensationPeriod;
import java.math.BigDecimal;

/** Compensation exactly as published by the provider. Never estimated. */
public record Compensation(
        BigDecimal min, BigDecimal max, String currency, CompensationPeriod period, String rawText) {}
