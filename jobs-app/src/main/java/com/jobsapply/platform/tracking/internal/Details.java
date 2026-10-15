package com.jobsapply.platform.tracking.internal;

import java.util.List;
import java.util.UUID;

/** Plaintext shapes of the encrypted {@code details} envelopes. Personal data: never log. */
public final class Details {

    private Details() {}

    public record ContactDetails(String name, String email, String phone, String title, String profileUrl, String notes) {
        @Override
        public String toString() {
            return "ContactDetails[redacted]";
        }
    }

    public record InterviewDetails(String location, String notes, List<UUID> contactIds) {
        @Override
        public String toString() {
            return "InterviewDetails[redacted]";
        }
    }

    public record FollowUpDetails(String note) {
        @Override
        public String toString() {
            return "FollowUpDetails[redacted]";
        }
    }
}
