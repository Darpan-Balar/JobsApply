package com.jobsapply.spi.source;

import java.time.Instant;

/**
 * Request for one page of jobs.
 *
 * @param cursor opaque cursor returned by the previous page, {@code null} for the first page
 * @param pageSize requested page size; adapters may return fewer
 * @param modifiedSince only honoured by adapters declaring {@code INCREMENTAL_SYNC}; may be {@code null}
 */
public record DiscoveryRequest(SourceBinding binding, String cursor, int pageSize, Instant modifiedSince) {}
