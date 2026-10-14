package com.jobsapply.platform.submission.internal;

import com.jobsapply.platform.application.ApplicationService;
import com.jobsapply.platform.shared.tenancy.UserContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * At startup, a submission left SUBMITTING (the application stopped mid-way) becomes SUBMISSION_UNCONFIRMED and is
 * never retried automatically: the candidate must verify what happened. Single-instance assumption (ADR-0008).
 *
 * <p>This is the one deliberate cross-user scan: it reads only ids and owners with plain SQL, then processes each
 * application inside its owner's {@link UserContext} through the normal, tenant-scoped service.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class SubmissionRecovery implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ApplicationService applications;

    record Interrupted(UUID applicationId, UUID ownerId) {}

    @Override
    public void run(ApplicationArguments args) {
        int recovered = recover();
        if (recovered > 0) {
            log.warn("Marked {} interrupted submission(s) as SUBMISSION_UNCONFIRMED; they need manual verification", recovered);
        }
    }

    /** @return how many interrupted submissions were marked unconfirmed */
    public int recover() {
        List<Interrupted> interrupted = jdbc.query("select id, owner_user_id from application where status = 'SUBMITTING'",
                (rs, i) -> new Interrupted(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)));
        for (Interrupted app : interrupted) {
            UserContext.runAs(app.ownerId(), () -> applications.markInterrupted(app.applicationId()));
        }
        return interrupted.size();
    }
}
