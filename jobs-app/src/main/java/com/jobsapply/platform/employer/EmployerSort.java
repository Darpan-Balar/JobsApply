package com.jobsapply.platform.employer;

import org.springframework.data.domain.Sort;

/** Supported orderings (a whitelist, so clients cannot sort by arbitrary or internal columns). */
public enum EmployerSort {
    PRIORITY(Sort.by(Sort.Order.desc("discoveryPriority"), Sort.Order.asc("displayName"))),
    NAME(Sort.by(Sort.Order.asc("displayName"))),
    KEY(Sort.by(Sort.Order.asc("key")));

    private final Sort sort;

    EmployerSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
