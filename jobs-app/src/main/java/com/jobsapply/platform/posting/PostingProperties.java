package com.jobsapply.platform.posting;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code jobs.postings.*}. @param staleAfter a not-closed posting unseen for longer than this is flagged stale */
@ConfigurationProperties("jobs.postings")
public record PostingProperties(Duration staleAfter) {

    public PostingProperties {
        staleAfter = staleAfter == null ? Duration.ofDays(3) : staleAfter;
    }
}
