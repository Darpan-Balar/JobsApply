package com.jobsapply.adapters.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.adapters.FakeTransport;
import com.jobsapply.adapters.Fixtures;
import com.jobsapply.adapters.RecordingSleeper;
import com.jobsapply.adapters.source.lever.LeverPostingsAdapter;
import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.DiscoveryRequest;
import com.jobsapply.spi.source.RawLocation;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LeverPostingsAdapterTest {

    static final String BASE = "https://api.lever.co/v0/postings/exampletrading";
    final FakeTransport transport = new FakeTransport();
    final LeverPostingsAdapter adapter = new LeverPostingsAdapter(Fixtures.fetcher(transport, new RecordingSleeper()), Fixtures.CLOCK);

    private DiscoveryPage page(SourceBinding binding, String cursor) {
        return ((PluginResult.Ok<DiscoveryPage>) adapter.discoverJobs(new DiscoveryRequest(binding, cursor, 100, null))).value();
    }

    @Test
    void pagesWithSkipAndLimitAndOnlyASinglePageIsComplete() {
        SourceBinding binding = Fixtures.binding("exampletrading", Map.of("pageSize", 2));
        transport.fixture(BASE + "?mode=json&skip=0&limit=2", "lever/page-1.json")
                .fixture(BASE + "?mode=json&skip=2&limit=2", "lever/page-2.json");

        DiscoveryPage first = page(binding, null);
        assertThat(first.nextCursor()).isEqualTo("2");
        assertThat(first.complete()).isFalse();

        DiscoveryPage second = page(binding, first.nextCursor());
        assertThat(second.nextCursor()).isNull();
        assertThat(second.complete()).as("a multi-page union is not proof of completeness").isFalse();

        SourceBinding big = Fixtures.binding("exampletrading", Map.of());
        transport.fixture(BASE + "?mode=json&skip=0&limit=250", "lever/page-1.json");
        assertThat(page(big, null).complete()).isTrue();
    }

    @Test
    void mapsPostingFields() {
        transport.fixture(BASE + "?mode=json&skip=0&limit=250", "lever/page-1.json");
        SourceJob quant = page(Fixtures.binding("exampletrading", Map.of()), null).jobs().getFirst();

        assertThat(quant.externalId()).isEqualTo("0a1b2c3d-1111-4a4a-8b8b-000000000001");
        assertThat(quant.title()).isEqualTo("Quantitative Developer");
        assertThat(quant.locations()).containsExactly(
                new RawLocation("London", null, null, "GB", null), new RawLocation("Remote - UK", null, null, null, null));
        assertThat(quant.department()).isEqualTo("Trading");
        assertThat(quant.employmentTypeRaw()).isEqualTo("Regular Full Time (Salary)");
        assertThat(quant.workplaceType()).as("'onsite' as returned in practice").isEqualTo(WorkplaceType.ONSITE);
        assertThat(quant.descriptionHtml()).contains("Low-latency", "<h3>Requirements</h3><ul><li>C++</li>", "Benefits");
        assertThat(quant.applicationUrl().toString()).endsWith("/apply");
        assertThat(quant.publishedAt()).as("createdAt is undocumented: not used as a date").isNull();
        assertThat(quant.sourceMetadata()).containsEntry("createdAtRaw", 1788199939703L);
        assertThat(quant.compensation().min()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(quant.compensation().currency()).isEqualTo("GBP");
        assertThat(quant.compensation().period()).isEqualTo(CompensationPeriod.YEAR);

        SourceJob platform = page(Fixtures.binding("exampletrading", Map.of()), null).jobs().get(1);
        assertThat(platform.workplaceType()).as("'unspecified' says nothing").isNull();
        assertThat(platform.department()).as("falls back to team").isEqualTo("Infra");
        assertThat(platform.compensation()).isNull();
    }

    @Test
    void euRegionUsesTheEuHost() {
        transport.respond("https://api.eu.lever.co/v0/postings/exampletrading?mode=json&skip=0&limit=250", 200, "[]");
        assertThat(page(Fixtures.binding("exampletrading", Map.of("region", "EU")), null).jobs()).isEmpty();
    }

    @Test
    void nonArrayBodyIsAParseFailure() {
        transport.respond(BASE + "?mode=json&skip=0&limit=250", 200, "{\"ok\":false}");
        assertThat(adapter.discoverJobs(new DiscoveryRequest(Fixtures.binding("exampletrading", Map.of()), null, 100, null)))
                .isInstanceOf(PluginResult.Failed.class);
    }
}
