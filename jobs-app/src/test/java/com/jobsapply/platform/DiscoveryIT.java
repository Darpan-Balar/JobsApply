package com.jobsapply.platform;

import static com.jobsapply.platform.support.DiscoveryFixture.job;
import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.failure;
import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.page;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.discovery.AvailabilityService;
import com.jobsapply.platform.discovery.SyncCounts;
import com.jobsapply.platform.discovery.SyncOutcome;
import com.jobsapply.platform.discovery.SyncReport;
import com.jobsapply.platform.discovery.SyncService;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.discovery.internal.SyncRunRepository;
import com.jobsapply.platform.employer.BindingView;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.posting.PostingQuery;
import com.jobsapply.platform.posting.PostingQueryService;
import com.jobsapply.platform.posting.PostingSort;
import com.jobsapply.platform.posting.PostingViews;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.DiscoveryFixture.Board;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.jobsapply.spi.source.DiscoveryPage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Discovery end to end against PostgreSQL, with a scripted source reproducing real-world source behaviour. */
@IntegrationTest
class DiscoveryIT {

    @Autowired SyncService sync;
    @Autowired AvailabilityService availability;
    @Autowired ScriptedJobSourceAdapter source;
    @Autowired DiscoveryFixture fixture;
    @Autowired PostingQueryService postings;
    @Autowired EmployerService employers;
    @Autowired SyncRunRepository runs;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    private SyncReport run(Board board) {
        return sync.sync(board.target(), SyncTrigger.MANUAL, false);
    }

    private List<PostingViews.Summary> postingsOf(Board board) {
        return postings.search(PostingQuery.builder().employerKey(board.employerKey()).build(), PostingSort.TITLE, 0, 100).items();
    }

    private String listingAvailability(Board board, String externalId) {
        return jdbc.queryForObject("select availability from job_listing where employer_source_id = ? and external_id = ?",
                String.class, board.target().employerSourceId(), externalId);
    }

    @Test
    void firstSyncInsertsAndAnUnchangedResyncChangesNothing() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "Backend Engineer"), job("b", "Data Engineer"))));

        SyncReport first = run(board);
        assertThat(first.outcome()).isEqualTo(SyncOutcome.SUCCEEDED);
        assertThat(first.counts()).isEqualTo(new SyncCounts(2, 2, 0, 0, 0, 0));

        SyncReport second = run(board);
        assertThat(second.counts()).isEqualTo(new SyncCounts(2, 0, 0, 2, 0, 0));
        assertThat(postingsOf(board)).hasSize(2).allSatisfy(p -> assertThat(p.availability()).isEqualTo(Availability.OPEN));
    }

    @Test
    void changedContentUpdatesListingAndPosting() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "Backend Engineer"))));
        run(board);
        source.script(board.externalRef(), List.of(completePage(job("a", "Senior Backend Engineer"))));

        assertThat(run(board).counts().updated()).isEqualTo(1);
        assertThat(postingsOf(board)).singleElement().satisfies(p -> {
            assertThat(p.title()).isEqualTo("Senior Backend Engineer");
            assertThat(p.seniority()).isEqualTo(Seniority.SENIOR);
        });
    }

    @Test
    void absenceFromACompleteListingClosesAndReappearanceReopens() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "Backend Engineer"), job("b", "Data Engineer"))));
        run(board);
        source.script(board.externalRef(), List.of(completePage(job("a", "Backend Engineer"))));

        assertThat(run(board).counts().closed()).isEqualTo(1);
        assertThat(listingAvailability(board, "b")).isEqualTo("CLOSED");
        assertThat(postingsOf(board)).filteredOn(p -> p.title().equals("Data Engineer"))
                .singleElement().satisfies(p -> assertThat(p.availability()).isEqualTo(Availability.CLOSED));

        source.script(board.externalRef(), List.of(completePage(job("a", "Backend Engineer"), job("b", "Data Engineer"))));
        assertThat(run(board).counts().updated()).as("reopened").isEqualTo(1);
        assertThat(listingAvailability(board, "b")).isEqualTo("OPEN");
    }

    @Test
    void aFailedListingNeverClosesAnything() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "Backend Engineer"))));
        run(board);
        source.script(board.externalRef(), List.of(failure(FailureKind.TRANSIENT)));

        SyncReport failed = run(board);

        assertThat(failed.outcome()).isEqualTo(SyncOutcome.FAILED);
        assertThat(failed.errors()).singleElement().asString().startsWith("listing failed: scripted TRANSIENT");
        assertThat(listingAvailability(board, "a")).isEqualTo("OPEN");
        BindingView binding = employers.get(board.employerKey()).bindings().getFirst();
        assertThat(binding.consecutiveFailures()).isEqualTo(1);
        assertThat(binding.lastError()).contains("scripted TRANSIENT");
        assertThat(jdbc.queryForObject("select count(*) from sync_error where sync_run_id = ?", Integer.class, failed.syncRunId())).isEqualTo(1);

        source.script(board.externalRef(), List.of(completePage(job("a", "Backend Engineer"))));
        run(board);
        assertThat(employers.get(board.employerKey()).bindings().getFirst().consecutiveFailures()).as("health recovers").isZero();
    }

    @Test
    void aFailedLaterPageIsPartialAndClosesNothing() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "A"), job("b", "B"), job("c", "C"))));
        run(board);
        source.script(board.externalRef(), List.of(
                page(0, true, false, List.of(job("a", "A")), List.of()),
                failure(FailureKind.TRANSIENT)));

        SyncReport partial = run(board);

        assertThat(partial.outcome()).isEqualTo(SyncOutcome.PARTIAL);
        assertThat(partial.counts().closed()).isZero();
        assertThat(partial.errors()).anySatisfy(e -> assertThat(e).startsWith("page 2 failed"));
        assertThat(partial.errors()).as("missing jobs were rechecked individually; the checks failed")
                .filteredOn(e -> e.startsWith("availability check")).hasSize(2);
        assertThat(listingAvailability(board, "b")).isEqualTo("OPEN");
    }

    @Test
    void anIncompleteListingRechecksMissingJobsIndividually() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "A"), job("gone", "Gone"), job("flaky", "Flaky"))));
        run(board);
        source.script(board.externalRef(), List.of(page(0, false, false, List.of(job("a", "A")), List.of())));
        source.availability(board.externalRef(), "gone", PluginResult.ok(
                new AvailabilityObservation(Availability.CLOSED, true, "HTTP 404 while board responded 200", Instant.now())));
        source.availability(board.externalRef(), "flaky", failure(FailureKind.TRANSIENT));

        SyncReport report = run(board);

        assertThat(report.outcome()).isEqualTo(SyncOutcome.PARTIAL);
        assertThat(report.counts().closed()).isEqualTo(1);
        assertThat(listingAvailability(board, "gone")).isEqualTo("CLOSED");
        assertThat(listingAvailability(board, "flaky")).as("a failed check is not evidence").isEqualTo("OPEN");
    }

    @Test
    void nonAuthoritativeObservationsDoNotClose() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "A"), job("b", "B"))));
        run(board);
        source.script(board.externalRef(), List.of(page(0, false, false, List.of(job("a", "A")), List.of())));
        source.availability(board.externalRef(), "b", PluginResult.ok(
                new AvailabilityObservation(Availability.CLOSED, false, "heuristic guess", Instant.now())));

        assertThat(run(board).counts().closed()).isZero();
        assertThat(listingAvailability(board, "b")).isEqualTo("OPEN");
    }

    @Test
    void unparseableItemsAreReportedAndNeverClosed() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "A"), job("b", "B"))));
        run(board);
        source.script(board.externalRef(), List.of(page(0, false, true, List.of(job("a", "A")),
                List.of(new DiscoveryPage.ItemProblem("b", "missing title")))));

        SyncReport report = run(board);
        assertThat(report.outcome()).isEqualTo(SyncOutcome.PARTIAL);
        assertThat(report.counts().failed()).isEqualTo(1);
        assertThat(listingAvailability(board, "b")).isEqualTo("OPEN");

        source.script(board.externalRef(), List.of(page(0, false, true, List.of(job("a", "A")),
                List.of(new DiscoveryPage.ItemProblem(null, "garbage")))));
        source.availability(board.externalRef(), "b", failure(FailureKind.TRANSIENT));
        assertThat(run(board).counts().closed()).as("an unidentified bad item makes the listing incomplete").isZero();
    }

    @Test
    void dryRunReportsButWritesNothing() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "A"), job("b", "B"))));

        SyncReport dryRun = sync.sync(board.target(), SyncTrigger.MANUAL, true);

        assertThat(dryRun.dryRun()).isTrue();
        assertThat(dryRun.counts().inserted()).isEqualTo(2);
        assertThat(postingsOf(board)).isEmpty();
        assertThat(jdbc.queryForObject("select dry_run from sync_run where id = ?", Boolean.class, dryRun.syncRunId())).isTrue();
        assertThat(employers.get(board.employerKey()).bindings().getFirst().lastSuccessAt()).as("health untouched").isNull();
    }

    @Test
    void aConcurrentRunForTheSameBindingIsSkippedAndCrashRecoveryReleasesIt() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "A"))));
        jdbc.update("insert into sync_run (id, job_source_id, employer_source_id, trigger, dry_run, status, started_at, created_at, "
                + "updated_at, version) values (?, ?, ?, 'SCHEDULED', false, 'RUNNING', now(), now(), now(), 0)",
                UUID.randomUUID(), board.target().jobSourceId(), board.target().employerSourceId());

        assertThat(run(board).outcome()).isEqualTo(SyncOutcome.SKIPPED_ALREADY_RUNNING);

        tx.executeWithoutResult(s -> runs.abortRunning(Instant.now())); // what startup recovery does
        assertThat(jdbc.queryForObject("select count(*) from sync_run where employer_source_id = ? and status = 'ABORTED'",
                Integer.class, board.target().employerSourceId())).isEqualTo(1);
        assertThat(run(board).outcome()).isEqualTo(SyncOutcome.SUCCEEDED);
    }

    @Test
    void theSameVacancyOnTwoSourcesBecomesOnePosting() {
        Board greenhouse = fixture.board();
        Board lever = fixture.board(greenhouse.employerKey());
        source.script(greenhouse.externalRef(), List.of(completePage(job("gh-1", "Backend Engineer", "Berlin, Germany"))));
        source.script(lever.externalRef(), List.of(completePage(job("lv-9", "Backend  Engineer!", "Berlin, Germany"))));

        run(greenhouse);
        run(lever);

        assertThat(postingsOf(greenhouse)).hasSize(1);
        UUID postingId = postingsOf(greenhouse).getFirst().id();
        assertThat(postings.get(postingId).listings()).extracting(PostingViews.Listing::externalId).containsExactly("gh-1", "lv-9");
    }

    @Test
    void identicalOpeningsOnTheSameBoardStaySeparate() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("1", "Backend Engineer"), job("2", "Backend Engineer"))));
        run(board);
        assertThat(postingsOf(board)).hasSize(2);
    }

    @Test
    void normalizationIsStoredWithoutGuessing() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.detailedJob("x"), job("y", "Engineer", "Somewhere"))));
        run(board);

        PostingViews.Summary detailed = postingsOf(board).stream().filter(p -> p.title().startsWith("Senior")).findFirst().orElseThrow();
        assertThat(detailed.seniority()).isEqualTo(Seniority.SENIOR);
        assertThat(detailed.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(detailed.workplaceType()).isEqualTo(WorkplaceType.REMOTE);
        assertThat(detailed.salary().currency()).isEqualTo("USD");
        assertThat(detailed.locations()).singleElement().satisfies(l -> {
            assertThat(l.countryCode()).isEqualTo("US");
            assertThat(l.remote()).isTrue();
        });

        PostingViews.Summary plain = postingsOf(board).stream().filter(p -> p.title().equals("Engineer")).findFirst().orElseThrow();
        assertThat(plain.seniority()).isEqualTo(Seniority.UNKNOWN);
        assertThat(plain.workplaceType()).isEqualTo(WorkplaceType.UNKNOWN);
        assertThat(plain.salary()).isNull();
        assertThat(plain.locations().getFirst().countryCode()).isNull();
        assertThat(postings.get(plain.id()).descriptionText()).isEqualTo("Build things.");
    }

    @Test
    void disabledBindingsAreNotSynced() {
        Board board = fixture.board();
        employers.disableBinding(board.employerKey(), board.sourceKey(), board.externalRef());
        assertThat(sync.sync(board.sourceKey(), null, SyncTrigger.MANUAL, false)).isEmpty();
    }

    @Test
    void availabilityRecheckBeforePreparation() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "A"))));
        run(board);
        UUID postingId = postingsOf(board).getFirst().id();
        source.availability(board.externalRef(), "a", PluginResult.ok(
                new AvailabilityObservation(Availability.CLOSED, true, "HTTP 404 while board responded 200", Instant.now())));

        AvailabilityService.AvailabilityReport report = availability.recheck(postingId);

        assertThat(report.availability()).isEqualTo(Availability.CLOSED);
        assertThat(report.checks()).singleElement().satisfies(c -> assertThat(c.method()).isEqualTo("job endpoint"));
    }

    @Test
    void staleIsAFlagNotAClosure() {
        Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "A"))));
        run(board);
        jdbc.update("update job_posting set first_seen_at = now() - interval '11 days', last_seen_at = now() - interval '10 days' "
                + "where employer_id = ?", board.target().employerId());

        assertThat(postings.search(PostingQuery.builder().employerKey(board.employerKey()).stale(true).build(), PostingSort.TITLE, 0, 10).items())
                .singleElement().satisfies(p -> {
                    assertThat(p.stale()).isTrue();
                    assertThat(p.availability()).isEqualTo(Availability.OPEN);
                });
    }
}
