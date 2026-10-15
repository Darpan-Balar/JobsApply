package com.jobsapply.platform;

import static com.jobsapply.platform.support.Eventually.eventually;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.candidate.CandidateEvents;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The audit log is fed through the event publication registry (ADR-0014): events are stored with the change, delivered
 * after commit, kept when delivery fails, delivered again on resubmission, and recorded only once.
 */
@IntegrationTest
class OutboxIT {

    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate transactions;
    @Autowired IncompleteEventPublications incomplete;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;

    private UUID user() {
        return users.createUser(NewUser.builder().username(uniqueKey("ob")).displayName("O").build()).id();
    }

    private CandidateEvents.VersionCreated event(UUID owner) {
        return new CandidateEvents.VersionCreated(UUID.randomUUID(), UUID.randomUUID(), 1, owner, null,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    private void publishInTransaction(Object... payloads) {
        transactions.executeWithoutResult(status -> {
            for (Object payload : payloads) {
                events.publishEvent(payload);
            }
        });
    }

    private long audited(UUID owner) {
        return jdbc.queryForObject("select count(*) from audit_event where subject_user_id = ?", Long.class, owner);
    }

    private long incompleteFor(UUID owner) {
        return jdbc.queryForObject("select count(*) from event_publication where completion_date is null and serialized_event like ?",
                Long.class, "%" + owner + "%");
    }

    @Test
    void anEventIsStoredWithTheChangeAndCompletedAfterDelivery() {
        UUID owner = user();
        publishInTransaction(event(owner));

        eventually(() -> assertThat(audited(owner)).isEqualTo(1));
        eventually(() -> assertThat(incompleteFor(owner)).isZero());
        assertThat(jdbc.queryForObject("select count(*) from event_publication where serialized_event like ?", Long.class, "%" + owner + "%"))
                .as("the publication was registered").isEqualTo(1);
    }

    @Test
    void aFailedDeliveryIsKeptAndSucceedsWhenResubmitted() {
        UUID owner = user();
        String function = "audit_unavailable_" + owner.toString().replace("-", "");
        jdbc.execute("create function " + function + "() returns trigger language plpgsql as $$ begin if new.subject_user_id = '" + owner
                + "' then raise exception 'audit store unavailable (test)'; end if; return new; end $$");
        jdbc.execute("create trigger " + function + " before insert on audit_event for each row execute function " + function + "()");
        try {
            publishInTransaction(event(owner));
            eventually(() -> assertThat(incompleteFor(owner)).as("delivery failed; the publication stays incomplete").isEqualTo(1));
            assertThat(audited(owner)).isZero();
        } finally {
            jdbc.execute("drop trigger " + function + " on audit_event");
            jdbc.execute("drop function " + function + "()");
        }

        incomplete.resubmitIncompletePublications(publication -> publication.getEvent() instanceof CandidateEvents.VersionCreated e
                && owner.equals(e.ownerId()));

        eventually(() -> assertThat(audited(owner)).isEqualTo(1));
        eventually(() -> assertThat(incompleteFor(owner)).isZero());
    }

    @Test
    void aRedeliveredEventIsRecordedOnce() {
        UUID owner = user();
        CandidateEvents.VersionCreated event = event(owner);

        publishInTransaction(event);
        eventually(() -> assertThat(audited(owner)).isEqualTo(1));
        publishInTransaction(event);
        eventually(() -> assertThat(incompleteFor(owner)).isZero());

        assertThat(audited(owner)).isEqualTo(1);
    }
}
