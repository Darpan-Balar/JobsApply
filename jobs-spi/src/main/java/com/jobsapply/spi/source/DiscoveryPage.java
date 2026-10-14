package com.jobsapply.spi.source;

import com.jobsapply.spi.common.Lists;
import java.util.List;

/**
 * @param nextCursor cursor for the next page, {@code null} when there is none
 * @param complete {@code true} only if the adapter is certain that the union of pages so far is the provider's full
 *     listing. Only a complete listing may be used as evidence that an absent job is closed.
 * @param problems items the adapter could not parse; they are skipped and reported, never guessed
 */
public record DiscoveryPage(List<SourceJob> jobs, String nextCursor, boolean complete, List<ItemProblem> problems) {

    /** @param externalRef the provider's identifier of the item, if it could be read */
    public record ItemProblem(String externalRef, String message) {}

    public DiscoveryPage {
        jobs = Lists.immutable(jobs);
        problems = Lists.immutable(problems);
    }

    public DiscoveryPage(List<SourceJob> jobs, String nextCursor, boolean complete) {
        this(jobs, nextCursor, complete, List.of());
    }
}
