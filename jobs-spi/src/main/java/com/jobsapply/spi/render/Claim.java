package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.profile.FactRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * A generated statement and the candidate facts that justify it. A claim must either cite facts (and be grounded in
 * them) or be content the candidate wrote and explicitly approved ({@code userApproved}). Anything else is invalid.
 */
public record Claim(@NotBlank String text, List<@Valid FactRef> sources, boolean userApproved) {

    public Claim {
        sources = Lists.immutable(sources);
    }

    public Claim(String text, List<FactRef> sources) {
        this(text, sources, false);
    }

    public static Claim userApproved(String text) {
        return new Claim(text, List.of(), true);
    }
}
