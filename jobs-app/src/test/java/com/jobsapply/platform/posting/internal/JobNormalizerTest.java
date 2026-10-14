package com.jobsapply.platform.posting.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.RawLocation;
import com.jobsapply.spi.source.SourceJob;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class JobNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Senior Backend Engineer|SENIOR",
        "Sr. Software Engineer|SENIOR",
        "Staff Engineer, Payments|STAFF",
        "Principal Engineer|PRINCIPAL",
        "Engineering Manager|MANAGER",
        "Senior Manager, Data|MANAGER",
        "Software Engineering Intern|INTERN",
        "Junior Developer|ENTRY",
        "New Grad Software Engineer|ENTRY",
        "Software Engineer II|MID",
        "Backend Engineer|UNKNOWN",
        "Leadership Development|UNKNOWN",
    })
    void seniorityComesOnlyFromExplicitKeywords(String title, Seniority expected) {
        assertThat(JobNormalizer.seniority(JobNormalizer.normalizeTitle(title))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Regular Full Time (Salary)|FULL_TIME",
        "FullTime|FULL_TIME",
        "Part-time|PART_TIME",
        "Contract|CONTRACT",
        "Temporary|TEMPORARY",
        "Intern|INTERNSHIP",
        "Remote|UNKNOWN",
    })
    void employmentTypeFromSourceValues(String raw, EmploymentType expected) {
        assertThat(JobNormalizer.employmentType(raw)).isEqualTo(expected);
    }

    @Test
    void titlesNormalizeButKeepLanguageSymbols() {
        assertThat(JobNormalizer.normalizeTitle("  C++ / C# Developer (Low-Latency)! ")).isEqualTo("c++ c# developer low latency");
    }

    @Test
    void workplaceIsExplicitOrUnambiguousOnly() {
        var remote = JobNormalizer.locations(List.of(new RawLocation("Remote - US", null, null, null, null)));
        var office = JobNormalizer.locations(List.of(new RawLocation("London", null, null, null, null)));
        assertThat(JobNormalizer.workplaceType(null, remote)).isEqualTo(WorkplaceType.REMOTE);
        assertThat(JobNormalizer.workplaceType(null, office)).as("not assumed on-site").isEqualTo(WorkplaceType.UNKNOWN);
        assertThat(JobNormalizer.workplaceType(WorkplaceType.HYBRID, office)).isEqualTo(WorkplaceType.HYBRID);
    }

    @Test
    void countriesResolveFromCodesOrTrailingNamesOnly() {
        var locations = JobNormalizer.locations(List.of(
                new RawLocation("Berlin, Germany", null, null, null, null),
                new RawLocation("London", null, null, "GB", null),
                new RawLocation("Remote - United States", null, null, null, null),
                new RawLocation("Springfield", null, null, null, null),
                new RawLocation("berlin, germany", null, null, null, null)));
        assertThat(locations).extracting(NormalizedJob.Location::countryCode).containsExactly("DE", "GB", "US", null);
    }

    @Test
    void invalidCompensationIsDroppedNotRepaired() {
        UUID employer = UUID.randomUUID();
        SourceJob base = SourceJob.builder().externalId("1").title("Engineer").build();
        assertThat(JobNormalizer.normalize(employer, base.toBuilder()
                .compensation(new Compensation(new BigDecimal("200"), new BigDecimal("100"), "USD", null, null)).build()).salaryMin()).isNull();
        assertThat(JobNormalizer.normalize(employer, base.toBuilder()
                .compensation(new Compensation(new BigDecimal("100"), null, "dollars", null, null)).build()).salaryMin()).isNull();
        assertThat(JobNormalizer.normalize(employer, base.toBuilder()
                .compensation(new Compensation(new BigDecimal("100.5"), null, "USD", null, null)).build()).salaryMin())
                .isEqualByComparingTo("100.50");
    }

    @Test
    void fingerprintIgnoresFormattingButNotSubstance() {
        UUID employer = UUID.randomUUID();
        var berlin = JobNormalizer.locations(List.of(new RawLocation("Berlin", null, null, null, null)));
        var munich = JobNormalizer.locations(List.of(new RawLocation("Munich", null, null, null, null)));
        String a = JobNormalizer.fingerprint(employer, JobNormalizer.normalizeTitle("Backend Engineer"), berlin);
        assertThat(JobNormalizer.fingerprint(employer, JobNormalizer.normalizeTitle("backend  engineer!"), berlin)).isEqualTo(a);
        assertThat(JobNormalizer.fingerprint(employer, JobNormalizer.normalizeTitle("Backend Engineer"), munich)).isNotEqualTo(a);
        assertThat(JobNormalizer.fingerprint(UUID.randomUUID(), JobNormalizer.normalizeTitle("Backend Engineer"), berlin)).isNotEqualTo(a);
    }

    @Test
    void descriptionHtmlBecomesPlainText() {
        assertThat(JobNormalizer.text("<div><p>Build &amp; run <script>x()</script><b>APIs</b></p></div>")).isEqualTo("Build & run APIs");
        assertThat(JobNormalizer.text("   ")).isNull();
    }
}
