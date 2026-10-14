package com.jobsapply.platform.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedactingLogCustomizerTest {

    @Test
    void masksEmailsAndPhoneNumbers() {
        assertThat(RedactingLogCustomizer.redact("contact jane.doe+jobs@example.co.uk now"))
                .isEqualTo("contact <email> now");
        assertThat(RedactingLogCustomizer.redact("call +44 20 7946 0958 or (555) 123-4567"))
                .isEqualTo("call <phone> or <phone>");
    }

    @Test
    void leavesOrdinaryDiagnosticsReadable() {
        String line = "sync 01a13918-ef2b-7038-9e33-4f60218436a4 at 2026-10-14T10:00:00Z took 1234 ms (v1.23.2), 17 jobs";
        assertThat(RedactingLogCustomizer.redact(line)).isEqualTo(line);
    }
}
