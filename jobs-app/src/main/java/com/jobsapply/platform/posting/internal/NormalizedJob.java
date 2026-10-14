package com.jobsapply.platform.posting.internal;

import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import java.math.BigDecimal;
import java.util.List;

/** A source job in canonical form. Raw values are kept on the listing; this is what postings are built from. */
record NormalizedJob(
        String title,
        String titleNormalized,
        Seniority seniority,
        EmploymentType employmentType,
        WorkplaceType workplaceType,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        CompensationPeriod salaryPeriod,
        String descriptionText,
        List<Location> locations,
        String fingerprint) {

    record Location(String rawText, String city, String region, String countryCode, boolean remote) {}
}
