package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import java.util.List;
import java.util.UUID;

/**
 * Format-independent resume content generated for one job from one candidate profile version. The header is the
 * candidate's own contact details (personal data, not claims); everything else is claims.
 */
public record ResumeDocument(String templateKey, UUID candidateProfileVersionId, Header header, List<Section> sections) {

    public ResumeDocument {
        sections = Lists.immutable(sections);
    }

    public record Header(String name, String email, String phone, String location, List<String> links) {
        public Header {
            links = Lists.immutable(links);
        }

        @Override
        public String toString() {
            return "Header[redacted]";
        }
    }

    public record Section(String heading, List<Claim> claims) {
        public Section {
            claims = Lists.immutable(claims);
        }
    }
}
