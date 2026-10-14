package com.jobsapply.platform.employer.internal;

import com.jobsapply.platform.employer.EmployerQuery;
import com.jobsapply.platform.source.SourceService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Resolves source filters to source ids, then runs the employer specification. */
@Component
@RequiredArgsConstructor
public class EmployerSearch {

    private final EmployerRepository employers;
    private final SourceService sources;

    public Page<Employer> find(EmployerQuery query, Pageable pageable) {
        return employers.findAll(EmployerSpecifications.matching(query, sourceIds(query)), pageable);
    }

    public List<Employer> findAll(EmployerQuery query, Sort sort) {
        return employers.findAll(EmployerSpecifications.matching(query, sourceIds(query)), sort);
    }

    /** {@code null} = no source filter; otherwise the sources satisfying both the source and adapter filters. */
    private Set<UUID> sourceIds(EmployerQuery query) {
        if (query.sourceKey() == null && query.adapterId() == null) {
            return null;
        }
        Set<UUID> ids = query.adapterId() != null ? sources.idsByAdapter(query.adapterId()) : null;
        if (query.sourceKey() != null) {
            UUID id = sources.idsByKey().get(query.sourceKey());
            Set<UUID> byKey = id == null ? Set.of() : Set.of(id);
            ids = ids == null ? byKey : byKey.stream().filter(ids::contains).collect(Collectors.toSet());
        }
        return ids;
    }
}
