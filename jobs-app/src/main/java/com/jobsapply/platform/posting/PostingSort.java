package com.jobsapply.platform.posting;

import org.springframework.data.domain.Sort;

public enum PostingSort {
    LAST_SEEN(Sort.by(Sort.Order.desc("lastSeenAt"), Sort.Order.asc("title"))),
    FIRST_SEEN(Sort.by(Sort.Order.desc("firstSeenAt"), Sort.Order.asc("title"))),
    TITLE(Sort.by(Sort.Order.asc("titleNormalized")));

    private final Sort sort;

    PostingSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
