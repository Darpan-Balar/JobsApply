package com.jobsapply.platform.candidate;

import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.profile.CandidateFact;
import com.jobsapply.spi.profile.CandidateSnapshot;
import java.util.function.Predicate;

/** Pure transformations of candidate snapshots. */
public final class CandidateSnapshots {

    private CandidateSnapshots() {}

    /** Sets {@code status} on every fact matching {@code which}. */
    public static CandidateSnapshot withVerification(CandidateSnapshot s, Predicate<CandidateFact> which, VerificationStatus status) {
        return s.toBuilder()
                .experiences(s.experiences().stream().map(f -> which.test(f) ? f.toBuilder().verification(status).build() : f).toList())
                .education(s.education().stream().map(f -> which.test(f) ? f.toBuilder().verification(status).build() : f).toList())
                .skills(s.skills().stream().map(f -> which.test(f) ? f.toBuilder().verification(status).build() : f).toList())
                .achievements(s.achievements().stream().map(f -> which.test(f) ? f.toBuilder().verification(status).build() : f).toList())
                .attributes(s.attributes().stream().map(f -> which.test(f) ? f.toBuilder().verification(status).build() : f).toList())
                .build();
    }

    /**
     * Imported data is not yet confirmed by the candidate: VERIFIED and DERIVED facts become UNVERIFIED. Stated
     * preferences and superseded facts keep their status.
     */
    public static CandidateSnapshot asUnconfirmedImport(CandidateSnapshot s) {
        return withVerification(s, f -> f.verification() == VerificationStatus.VERIFIED || f.verification() == VerificationStatus.DERIVED,
                VerificationStatus.UNVERIFIED);
    }
}
