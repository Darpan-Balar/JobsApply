package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import java.util.List;
import java.util.UUID;

/** Format-independent resume content generated for one job from one candidate profile version. */
public record ResumeDocument(String templateKey, UUID candidateProfileVersionId, List<Section> sections) {

    public ResumeDocument {
        sections = Lists.immutable(sections);
    }

    public record Section(String heading, List<Claim> claims) {
        public Section {
            claims = Lists.immutable(claims);
        }
    }
}
