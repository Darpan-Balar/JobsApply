package com.jobsapply.platform.employer.internal;

import com.jobsapply.platform.employer.EmployerQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

/** Specification pattern: translates an {@link EmployerQuery} into JPA criteria (no native SQL; ADR-0002). */
final class EmployerSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private EmployerSpecifications() {}

    /**
     * @param sourceIds when non-null, restrict to employers with an enabled binding on one of these sources (an empty
     *     set matches nothing)
     */
    static Specification<Employer> matching(EmployerQuery query, Set<UUID> sourceIds) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!query.includeArchived()) {
                predicates.add(cb.isNull(root.get("archivedAt")));
            }
            if (query.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), query.enabled()));
            }
            if (query.minPriority() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("discoveryPriority"), query.minPriority().shortValue()));
            }
            if (query.industry() != null) {
                predicates.add(cb.equal(cb.lower(root.get("industry")), query.industry().toLowerCase(Locale.ROOT)));
            }
            if (query.tag() != null) {
                predicates.add(((HibernateCriteriaBuilder) cb).collectionContains(root.get("tags"), query.tag().toLowerCase(Locale.ROOT)));
            }
            if (query.text() != null && !query.text().isBlank()) {
                String pattern = "%" + escapeLike(query.text().strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("displayName")), pattern, LIKE_ESCAPE),
                        cb.like(root.get("key"), pattern, LIKE_ESCAPE)));
            }
            if (sourceIds != null) {
                if (sourceIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    Subquery<UUID> bound = criteria.subquery(UUID.class);
                    var binding = bound.from(EmployerSource.class);
                    bound.select(binding.get("employerId")).where(
                            cb.equal(binding.get("employerId"), root.get("id")),
                            binding.get("jobSourceId").in(sourceIds),
                            cb.isTrue(binding.get("enabled")));
                    predicates.add(cb.exists(bound));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
