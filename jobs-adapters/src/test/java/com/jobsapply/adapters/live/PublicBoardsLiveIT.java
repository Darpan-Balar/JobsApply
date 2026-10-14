package com.jobsapply.adapters.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.adapters.http.HostRateLimiter;
import com.jobsapply.adapters.http.HttpFetcher;
import com.jobsapply.adapters.http.JdkHttpTransport;
import com.jobsapply.adapters.http.Sleeper;
import com.jobsapply.adapters.source.ashby.AshbyJobBoardAdapter;
import com.jobsapply.adapters.source.greenhouse.GreenhouseJobBoardAdapter;
import com.jobsapply.adapters.source.lever.LeverPostingsAdapter;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.DiscoveryRequest;
import com.jobsapply.spi.source.JobSourceAdapter;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Opt-in checks against the real public APIs (read-only GETs): {@code ./mvnw -pl jobs-adapters verify -Plive}.
 * Boards default to the providers' own public/demo boards; override with -Dlive.greenhouse.board=... etc.
 * They verify that today's real payloads still parse; they never run in the default build.
 */
class PublicBoardsLiveIT {

    private final HttpFetcher fetcher = new HttpFetcher(new JdkHttpTransport(),
            new HostRateLimiter(Clock.systemUTC(), Sleeper.SYSTEM), Sleeper.SYSTEM);

    private static DiscoveryPage firstPage(JobSourceAdapter adapter, String board) {
        PluginResult<DiscoveryPage> result = adapter.discoverJobs(
                new DiscoveryRequest(new SourceBinding("live", board, Map.of()), null, 100, null));
        assertThat(result).as("listing of %s", board).isInstanceOf(PluginResult.Ok.class);
        return ((PluginResult.Ok<DiscoveryPage>) result).value();
    }

    private static void assertParsed(DiscoveryPage page) {
        assertThat(page.problems()).as("no unparseable items").isEmpty();
        assertThat(page.jobs()).allSatisfy(job -> {
            assertThat(job.externalId()).isNotBlank();
            assertThat(job.title()).isNotBlank();
            assertThat(job.listingUrl()).isNotNull();
        });
    }

    @Test
    void greenhouse() {
        DiscoveryPage page = firstPage(new GreenhouseJobBoardAdapter(fetcher, Clock.systemUTC()),
                System.getProperty("live.greenhouse.board", "greenhouse"));
        assertParsed(page);
        assertThat(page.complete()).isTrue();
        System.out.printf("greenhouse: %d jobs, e.g. %s%n", page.jobs().size(), page.jobs().stream().findFirst().map(SourceJob::title).orElse("-"));
    }

    @Test
    void lever() {
        DiscoveryPage page = firstPage(new LeverPostingsAdapter(fetcher, Clock.systemUTC()),
                System.getProperty("live.lever.board", "leverdemo"));
        assertParsed(page);
        System.out.printf("lever: %d jobs (complete=%s)%n", page.jobs().size(), page.complete());
    }

    @Test
    void ashby() {
        DiscoveryPage page = firstPage(new AshbyJobBoardAdapter(fetcher, Clock.systemUTC()),
                System.getProperty("live.ashby.board", "ashby"));
        assertParsed(page);
        assertThat(page.complete()).isTrue();
        long withSalary = page.jobs().stream().filter(j -> j.compensation() != null && j.compensation().currency() != null).count();
        System.out.printf("ashby: %d jobs, %d with a salary range%n", page.jobs().size(), withSalary);
    }
}
