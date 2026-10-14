package com.jobsapply.adapters.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.adapters.FakeTransport;
import com.jobsapply.adapters.Fixtures;
import com.jobsapply.adapters.RecordingSleeper;
import com.jobsapply.adapters.source.ashby.AshbyJobBoardAdapter;
import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.RawLocation;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import com.jobsapply.spi.source.SourceJobRef;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AshbyJobBoardAdapterTest {

    static final String URL = "https://api.ashbyhq.com/posting-api/job-board/exampleco?includeCompensation=true";
    final FakeTransport transport = new FakeTransport().fixture(URL, "ashby/job-board.json");
    final AshbyJobBoardAdapter adapter = new AshbyJobBoardAdapter(Fixtures.fetcher(transport, new RecordingSleeper()), Fixtures.CLOCK);
    final SourceBinding binding = Fixtures.binding("exampleco", Map.of());

    private DiscoveryPage page() {
        return ((PluginResult.Ok<DiscoveryPage>) adapter.discoverJobs(Fixtures.firstPage(binding))).value();
    }

    @Test
    void returnsTheCompleteListingWithoutUnlistedPostings() {
        DiscoveryPage page = page();
        assertThat(page.complete()).isTrue();
        assertThat(page.jobs()).extracting(SourceJob::externalId)
                .containsExactly("7458d4e9-da2e-47bd-98cb-adfda43d42b2", "9f2c1a7e-0000-4000-8000-000000000002");
    }

    @Test
    void mapsFieldsLocationsAndSalaryOnly() {
        SourceJob job = page().jobs().getFirst();
        assertThat(job.workplaceType()).isEqualTo(WorkplaceType.HYBRID);
        assertThat(job.employmentTypeRaw()).isEqualTo("FullTime");
        assertThat(job.locations()).containsExactly(
                new RawLocation("Berlin", "Berlin", "Berlin", "DE", false),
                new RawLocation("Remote - Europe", null, null, null, null));
        assertThat(job.compensation().min()).isEqualByComparingTo(new BigDecimal("110000"));
        assertThat(job.compensation().currency()).isEqualTo("EUR");
        assertThat(job.compensation().period()).isEqualTo(CompensationPeriod.YEAR);
        assertThat(job.compensation().rawText()).startsWith("€110K");
        assertThat(job.publishedAt()).isNotNull();

        SourceJob remote = page().jobs().get(1);
        assertThat(remote.externalId()).as("derived from jobUrl when id is absent").isEqualTo("9f2c1a7e-0000-4000-8000-000000000002");
        assertThat(remote.locations().getFirst().remote()).isTrue();
        assertThat(remote.compensation()).isNull();
    }

    @Test
    void noSingleJobEndpointMeansUnsupportedNotGuessed() {
        assertThat(adapter.fetchJobDetails(new SourceJobRef(binding, "x"))).isInstanceOf(PluginResult.Unsupported.class);
        assertThat(adapter.checkAvailability(new SourceJobRef(binding, "x"))).isInstanceOf(PluginResult.Unsupported.class);
    }

    @Test
    void compensationCanBeDisabled() {
        FakeTransport t = new FakeTransport().respond("https://api.ashbyhq.com/posting-api/job-board/exampleco", 200, "{\"jobs\":[]}");
        AshbyJobBoardAdapter a = new AshbyJobBoardAdapter(Fixtures.fetcher(t, new RecordingSleeper()), Fixtures.CLOCK);
        assertThat(a.discoverJobs(Fixtures.firstPage(Fixtures.binding("exampleco", Map.of("includeCompensation", false)))))
                .isInstanceOf(PluginResult.Ok.class);
    }
}
