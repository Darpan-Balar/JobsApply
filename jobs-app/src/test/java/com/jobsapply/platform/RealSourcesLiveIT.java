package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.discovery.SyncOutcome;
import com.jobsapply.platform.discovery.SyncReport;
import com.jobsapply.platform.discovery.SyncService;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.employer.BindingSpec;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.EmployerSpec;
import com.jobsapply.platform.source.JobSourceSpec;
import com.jobsapply.platform.source.SourceService;
import com.jobsapply.platform.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Opt-in (-Plive): the full pipeline — real adapter, sync engine, PostgreSQL — against real public boards, twice.
 * The second sync must insert and close nothing. Uses the providers' own public/demo boards.
 */
@IntegrationTest
class RealSourcesLiveIT {

    @Autowired SourceService sources;
    @Autowired EmployerService employers;
    @Autowired SyncService sync;

    @ParameterizedTest
    @CsvSource({"greenhouse-job-board,greenhouse", "lever-postings,leverdemo", "ashby-job-board,ashby"})
    void syncsARealBoardIdempotently(String adapter, String board) {
        String source = uniqueKey("live");
        String employer = uniqueKey("live");
        sources.upsert(new JobSourceSpec(source, adapter, "Live " + adapter, true, Map.of()));
        employers.create(EmployerSpec.builder().key(employer).displayName("Live " + board).discoveryPriority(50).enabled(true).build(),
                List.of(BindingSpec.of(source, board)));

        SyncReport first = sync.sync(source, employer, SyncTrigger.MANUAL, false).getFirst();
        SyncReport second = sync.sync(source, employer, SyncTrigger.MANUAL, false).getFirst();
        System.out.printf("%s/%s first=%s %s second=%s %s%n", adapter, board, first.outcome(), first.counts(), second.outcome(), second.counts());

        assertThat(first.outcome()).isEqualTo(SyncOutcome.SUCCEEDED);
        assertThat(first.counts().inserted()).isPositive();
        assertThat(second.outcome()).isEqualTo(SyncOutcome.SUCCEEDED);
        assertThat(second.counts().inserted()).isZero();
        assertThat(second.counts().closed()).isZero();
    }
}
