package com.jobsapply.spi.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defensive-copy helpers for contract records. Unlike {@link List#copyOf}, these tolerate {@code null} elements so
 * that Bean Validation (not a {@link NullPointerException}) reports them with a precise path.
 */
public final class Lists {

    private Lists() {}

    public static <T> List<T> immutable(List<T> source) {
        return source == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(source));
    }

    public static <K, V> Map<K, V> immutable(Map<K, V> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
