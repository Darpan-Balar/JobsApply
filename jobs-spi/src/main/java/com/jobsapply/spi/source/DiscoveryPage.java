package com.jobsapply.spi.source;

import com.jobsapply.spi.common.Lists;
import java.util.List;

/**
 * @param nextCursor cursor for the next page, {@code null} when there is none
 * @param complete {@code true} only if the adapter is certain that the union of pages so far is the provider's full
 *     listing. Only a complete listing may be used as evidence that an absent job is closed.
 */
public record DiscoveryPage(List<SourceJob> jobs, String nextCursor, boolean complete) {

    public DiscoveryPage {
        jobs = Lists.immutable(jobs);
    }
}
