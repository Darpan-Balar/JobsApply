package com.jobsapply.platform.shared.model;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Stable, framework-independent page representation for APIs. {@code page} is zero-based. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public PageResult {
        items = List.copyOf(items);
    }

    public static <S, T> PageResult<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResult<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
