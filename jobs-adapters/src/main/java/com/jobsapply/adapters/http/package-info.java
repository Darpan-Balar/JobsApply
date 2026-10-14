/**
 * Shared HTTP machinery for job-source adapters: transport abstraction, bounded retries with backoff, per-host rate
 * limiting, response-size limits and the HTTP-status → {@link com.jobsapply.spi.common.FailureKind} mapping.
 * Framework-free: depends only on the JDK, Jackson and the SPI.
 */
package com.jobsapply.adapters.http;
