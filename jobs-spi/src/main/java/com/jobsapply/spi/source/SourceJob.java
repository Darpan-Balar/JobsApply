package com.jobsapply.spi.source;

import com.jobsapply.spi.common.Lists;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * A job as reported by a source, before normalization. Every optional field is {@code null} when the provider did not
 * supply it — adapters must never fabricate dates, compensation or URLs.
 */
@Builder(toBuilder = true)
public record SourceJob(
        String externalId,
        String title,
        List<RawLocation> locations,
        String department,
        String employmentTypeRaw,
        String descriptionHtml,
        URI listingUrl,
        URI applicationUrl,
        Instant publishedAt,
        Instant updatedAt,
        Compensation compensation,
        Map<String, Object> sourceMetadata) {

    public SourceJob {
        locations = Lists.immutable(locations);
        sourceMetadata = Lists.immutable(sourceMetadata);
    }
}
