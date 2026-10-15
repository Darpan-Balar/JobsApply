package com.jobsapply.platform;

import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.operations.internal.ScheduledJobs;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Scheduled synchronization and retention: single-flight across processes, and retention never touches live data. */
@IntegrationTest
class OperationsIT {

    @Autowired ScheduledJobs jobs;
    @Autowired DiscoveryFixture fixture;
    @Autowired ScriptedJobSourceAdapter source;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    private DiscoveryFixture.Board scriptedBoard() {
        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Backend Engineer"))));
        return board;
    }

    @Test
    void aScheduledSyncIsSkippedWhileAnotherHoldsTheLock() throws Exception {
        scriptedBoard();
        try (Connection other = dataSource.getConnection(); Statement statement = other.createStatement()) {
            statement.execute("select pg_advisory_lock(hashtextextended('jobs:scheduled-sync', 0))");
            assertThat(jobs.runSync()).as("another process is syncing").isNull();
            statement.execute("select pg_advisory_unlock(hashtextextended('jobs:scheduled-sync', 0))");
        }
    }

    @Test
    void theScheduledSyncRecordsItsRunsAsScheduled() {
        DiscoveryFixture.Board board = scriptedBoard();

        ScheduledJobs.SyncSummary summary = jobs.runSync();

        assertThat(summary).isNotNull();
        assertThat(summary.bindings()).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from sync_run where employer_source_id = ? and trigger = 'SCHEDULED'", Long.class,
                board.target().employerSourceId())).isEqualTo(1);
    }

    @Test
    void retentionDeletesOnlyOldFinishedRunsAndDeliveredEvents() {
        DiscoveryFixture.Board board = scriptedBoard();
        jobs.runSync();
        UUID binding = board.target().employerSourceId();
        UUID old = jdbc.queryForObject("select id from sync_run where employer_source_id = ? limit 1", UUID.class, binding);
        jdbc.update("update sync_run set started_at = now() - interval '200 days', finished_at = now() - interval '200 days' where id = ?", old);
        UUID oldRunning = UUID.randomUUID();
        jdbc.update("""
                insert into sync_run (id, job_source_id, employer_source_id, trigger, dry_run, status, started_at, discovered_count,
                    inserted_count, updated_count, unchanged_count, closed_count, failed_count, created_at, updated_at, version)
                select ?, job_source_id, null, 'MANUAL', false, 'RUNNING', now() - interval '200 days', 0, 0, 0, 0, 0, 0, now(), now(), 0
                from sync_run where id = ?""", oldRunning, old);
        UUID delivered = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        jdbc.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date, status) "
                + "values (?, 'test', 'java.lang.String', '\"x\"', now() - interval '30 days', now() - interval '30 days', 'COMPLETED')", delivered);
        jdbc.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date) "
                + "values (?, 'test', 'java.lang.String', '\"x\"', now() - interval '30 days')", pending);
        try {
            ScheduledJobs.RetentionSummary summary = jobs.runRetention();

            assertThat(summary.syncRunsDeleted()).isPositive();
            assertThat(jdbc.queryForObject("select count(*) from sync_run where id = ?", Long.class, old)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from sync_run where id = ?", Long.class, oldRunning)).as("never a running one").isOne();
            assertThat(jdbc.queryForObject("select count(*) from event_publication where id = ?", Long.class, delivered)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from event_publication where id = ?", Long.class, pending))
                    .as("an undelivered event is kept for retry").isOne();
        } finally {
            jdbc.update("delete from event_publication where id in (?, ?)", delivered, pending);
            jdbc.update("delete from sync_run where id = ?", oldRunning);
        }
    }
}
