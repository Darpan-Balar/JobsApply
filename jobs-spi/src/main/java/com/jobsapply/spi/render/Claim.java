package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.profile.FactRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** A generated statement and the candidate facts that justify it. A claim without sources is invalid. */
public record Claim(@NotBlank String text, @NotEmpty List<@Valid FactRef> sources) {

    public Claim {
        sources = Lists.immutable(sources);
    }
}
