package com.jobsapply.platform.audit.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /** Audit events are not tenant-filtered; every query is explicitly scoped to a subject user. */
    @Query("select e from AuditEvent e where e.subjectUserId = :subject and (:aggregateId is null or e.aggregateId = :aggregateId) "
            + "order by e.occurredAt desc")
    List<AuditEvent> findForSubject(UUID subject, UUID aggregateId, Pageable pageable);
}
