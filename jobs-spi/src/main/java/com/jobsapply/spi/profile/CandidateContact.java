package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.Lists;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import lombok.Builder;

/** Personal contact details (PII). Never logged; encrypted at rest from Phase 4. */
@Builder(toBuilder = true)
public record CandidateContact(
        String fullName,
        @Email String email,
        String phone,
        String city,
        @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        List<URI> links) {

    public CandidateContact {
        links = Lists.immutable(links);
    }

    @Override
    public String toString() {
        return "CandidateContact[redacted]";
    }
}
