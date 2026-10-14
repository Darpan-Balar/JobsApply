package com.jobsapply.adapters.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.adapters.FakeTransport;
import com.jobsapply.adapters.Fixtures;
import com.jobsapply.adapters.RecordingSleeper;
import com.jobsapply.adapters.source.greenhouse.GreenhouseJobBoardAdapter;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import com.jobsapply.spi.source.SourceJobRef;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fixtures are synthetic, shaped after the documented and observed (2026-10-14) Job Board API responses. */
class GreenhouseJobBoardAdapterTest {

    static final String LIST = "https://boards-api.greenhouse.io/v1/boards/examplefintech/jobs?content=true";
    static final String PROBE = "https://boards-api.greenhouse.io/v1/boards/examplefintech/jobs";
    static final String JOB = "https://boards-api.greenhouse.io/v1/boards/examplefintech/jobs/4001001?pay_transparency=true";

    final FakeTransport transport = new FakeTransport();
    final GreenhouseJobBoardAdapter adapter = new GreenhouseJobBoardAdapter(Fixtures.fetcher(transport, new RecordingSleeper()), Fixtures.CLOCK);
    final SourceBinding binding = Fixtures.binding("examplefintech", Map.of("maxRetries", 0));

    @Test
    void parsesTheBoardAndReportsUnparseableItems() {
        transport.fixture(LIST, "greenhouse/jobs.json");

        DiscoveryPage page = ((PluginResult.Ok<DiscoveryPage>) adapter.discoverJobs(Fixtures.firstPage(binding))).value();

        assertThat(page.complete()).as("meta.total equals the number of jobs returned").isTrue();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.jobs()).extracting(SourceJob::externalId).containsExactly("4001001", "4001002");
        assertThat(page.problems()).singleElement().satisfies(p -> assertThat(p.message()).contains("without id"));

        SourceJob job = page.jobs().getFirst();
        assertThat(job.title()).isEqualTo("Senior Backend Engineer");
        assertThat(job.locations()).singleElement().satisfies(l -> assertThat(l.text()).isEqualTo("Remote - United States"));
        assertThat(job.department()).isEqualTo("Engineering");
        assertThat(job.descriptionHtml()).isEqualTo("<div><p>Build &amp; run <strong>payments</strong> APIs in Java.</p></div>");
        assertThat(job.publishedAt()).isEqualTo(Instant.parse("2026-08-24T17:52:04Z"));
        assertThat(job.updatedAt()).isEqualTo(Instant.parse("2026-10-09T22:28:27Z"));
        assertThat(job.listingUrl()).isEqualTo(URI.create("https://boards.greenhouse.io/examplefintech/jobs/4001001"));
        assertThat(job.employmentTypeRaw()).as("not provided by this API").isNull();
        assertThat(job.compensation()).as("list endpoint has no pay data").isNull();
        assertThat(job.sourceMetadata()).containsEntry("requisitionId", "REQ-17").containsEntry("companyName", "Example Fintech");

        assertThat(page.jobs().get(1).publishedAt()).as("never fabricated when absent").isNull();
    }

    @Test
    void countMismatchMeansIncompleteListing() {
        transport.respond(LIST, 200, FakeTransport.read("greenhouse/jobs.json").replace("\"total\": 3", "\"total\": 50"));
        assertThat(((PluginResult.Ok<DiscoveryPage>) adapter.discoverJobs(Fixtures.firstPage(binding))).value().complete()).isFalse();
    }

    @Test
    void detailsCombinePayRangesWithoutInventingAnInterval() {
        transport.fixture(JOB, "greenhouse/job-with-pay.json");

        SourceJob job = ((PluginResult.Ok<SourceJob>) adapter.fetchJobDetails(new SourceJobRef(binding, "4001001"))).value();

        assertThat(job.compensation().min()).isEqualByComparingTo(new BigDecimal("135000.00"));
        assertThat(job.compensation().max()).isEqualByComparingTo(new BigDecimal("190000.00"));
        assertThat(job.compensation().currency()).isEqualTo("USD");
        assertThat(job.compensation().period()).isNull();
        assertThat(job.compensation().rawText()).isEqualTo("NYC; Other US");
    }

    @Test
    void availabilityIsAuthoritativeOnlyWhenTheBoardStillExists() {
        transport.respond(JOB, 200, "{\"id\":4001001,\"title\":\"x\"}");
        assertThat(observe()).satisfies(o -> {
            assertThat(o.availability()).isEqualTo(Availability.OPEN);
            assertThat(o.authoritative()).isTrue();
        });

        FakeTransport closed = new FakeTransport().respond(JOB, 404, "{}").respond(PROBE, 200, "{\"jobs\":[],\"meta\":{\"total\":0}}");
        AvailabilityObservation observation = ((PluginResult.Ok<AvailabilityObservation>) adapterWith(closed)
                .checkAvailability(new SourceJobRef(binding, "4001001"))).value();
        assertThat(observation.availability()).isEqualTo(Availability.CLOSED);
        assertThat(observation.evidence()).contains("404", "board responded 200");

        FakeTransport boardGone = new FakeTransport().respond(JOB, 404, "{}").respond(PROBE, 404, "{}");
        assertThat(adapterWith(boardGone).checkAvailability(new SourceJobRef(binding, "4001001")))
                .as("a missing board says nothing about the job")
                .isInstanceOfSatisfying(PluginResult.Failed.class, f -> assertThat(f.kind()).isEqualTo(FailureKind.NOT_FOUND));
    }

    @Test
    void missingBoardIsAFailureNotAnEmptyListing() {
        transport.respond(LIST, 404, "{}");
        assertThat(adapter.discoverJobs(Fixtures.firstPage(binding)))
                .isInstanceOfSatisfying(PluginResult.Failed.class, f -> assertThat(f.kind()).isEqualTo(FailureKind.NOT_FOUND));
    }

    @Test
    void invalidBindingsAndIdsFailCleanly() {
        assertThat(adapter.validateConfiguration(Fixtures.binding("../admin", Map.of())).isValid()).isFalse();
        assertThat(adapter.validateConfiguration(Fixtures.binding("examplefintech", Map.of("maxRetries", 99))).isValid())
                .as("bean constraints are checked by the platform binder; type errors here").isTrue();
        assertThat(adapter.validateConfiguration(Fixtures.binding("examplefintech", Map.of("requestTimeout", "PT0S"))).isValid()).isFalse();
        assertThat(adapter.fetchJobDetails(new SourceJobRef(binding, "not-numeric")))
                .isInstanceOfSatisfying(PluginResult.Failed.class, f -> assertThat(f.kind()).isEqualTo(FailureKind.PERMANENT));
        assertThat(transport.requests).isEmpty();
    }

    private AvailabilityObservation observe() {
        return ((PluginResult.Ok<AvailabilityObservation>) adapter.checkAvailability(new SourceJobRef(binding, "4001001"))).value();
    }

    private GreenhouseJobBoardAdapter adapterWith(FakeTransport t) {
        return new GreenhouseJobBoardAdapter(Fixtures.fetcher(t, new RecordingSleeper()), Fixtures.CLOCK);
    }
}
