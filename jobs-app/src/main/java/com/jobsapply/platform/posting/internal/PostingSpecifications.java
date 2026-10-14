package com.jobsapply.platform.posting.internal;

import com.jobsapply.platform.posting.PostingQuery;
import com.jobsapply.spi.common.Availability;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Specification pattern for posting search (JPA criteria only; no native SQL). */
public final class PostingSpecifications {

    private PostingSpecifications() {}

    public static Specification<JobPosting> matching(PostingQuery query, UUID employerId, Instant staleBefore) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (employerId != null) {
                predicates.add(cb.equal(root.get("employerId"), employerId));
            }
            if (query.availability() != null) {
                predicates.add(cb.equal(root.get("availability"), query.availability()));
            }
            if (query.workplaceType() != null) {
                predicates.add(cb.equal(root.get("workplaceType"), query.workplaceType()));
            }
            if (query.employmentType() != null) {
                predicates.add(cb.equal(root.get("employmentType"), query.employmentType()));
            }
            if (query.seniority() != null) {
                predicates.add(cb.equal(root.get("seniority"), query.seniority()));
            }
            if (query.seenSince() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastSeenAt"), query.seenSince()));
            }
            if (query.stale() != null) {
                Predicate stale = cb.and(cb.notEqual(root.get("availability"), Availability.CLOSED),
                        cb.lessThan(root.get("lastSeenAt"), staleBefore));
                predicates.add(query.stale() ? stale : cb.not(stale));
            }
            if (query.text() != null && !query.text().isBlank()) {
                String pattern = "%" + query.text().strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern, '\\'));
            }
            if (query.countryCode() != null) {
                Subquery<UUID> located = criteria.subquery(UUID.class);
                var location = located.from(JobPostingLocation.class);
                located.select(location.get("posting").get("id")).where(
                        cb.equal(location.get("posting"), root),
                        cb.equal(location.get("countryCode"), query.countryCode().toUpperCase(Locale.ROOT)));
                predicates.add(cb.exists(located));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
