package com.jobsapply.platform.support;

import static com.jobsapply.platform.support.TestData.uniqueKey;

import com.jobsapply.platform.employer.BindingSpec;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.EmployerSpec;
import com.jobsapply.platform.employer.SyncTarget;
import com.jobsapply.platform.source.JobSourceSpec;
import com.jobsapply.platform.source.SourceService;
import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.RawLocation;
import com.jobsapply.spi.source.SourceJob;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Creates a scripted source + employer + binding and builds source jobs for discovery tests. */
@Component
public class DiscoveryFixture {

    private final SourceService sources;
    private final EmployerService employers;

    public DiscoveryFixture(SourceService sources, EmployerService employers) {
        this.sources = sources;
        this.employers = employers;
    }

    public record Board(String employerKey, String sourceKey, String externalRef, SyncTarget target) {}

    public Board board() {
        return board(uniqueKey("emp"));
    }

    /** A new scripted source bound to the given (created if missing) employer. */
    public Board board(String employerKey) {
        String sourceKey = uniqueKey("scripted");
        String ref = uniqueKey("board");
        sources.upsert(new JobSourceSpec(sourceKey, ScriptedJobSourceAdapter.ID, "Scripted", true, Map.of()));
        if (employers.findId(employerKey).isEmpty()) {
            employers.create(EmployerSpec.builder().key(employerKey).displayName("Employer " + employerKey)
                    .discoveryPriority(50).enabled(true).build(), List.of());
        }
        employers.putBinding(employerKey, BindingSpec.of(sourceKey, ref));
        SyncTarget target = employers.syncTargets(sourceKey, employerKey).getFirst();
        return new Board(employerKey, sourceKey, ref, target);
    }

    public static SourceJob job(String id, String title) {
        return job(id, title, "Berlin, Germany");
    }

    public static SourceJob job(String id, String title, String location) {
        return SourceJob.builder()
                .externalId(id)
                .title(title)
                .locations(List.of(new RawLocation(location, null, null, null, null)))
                .employmentTypeRaw("Full-time")
                .descriptionHtml("<p>Build <b>things</b>.</p>")
                .listingUrl(URI.create("https://jobs.example/" + id))
                .applicationUrl(URI.create("https://jobs.example/" + id + "/apply"))
                .build();
    }

    public static SourceJob detailedJob(String id) {
        return job(id, "Senior Backend Engineer (Java)", "Remote - United States").toBuilder()
                .workplaceType(WorkplaceType.REMOTE)
                .compensation(new Compensation(new BigDecimal("150000"), new BigDecimal("190000"), "USD",
                        CompensationPeriod.YEAR, "base"))
                .build();
    }
}
