package com.jobsapply.platform.configimport.internal;

import com.jobsapply.platform.catalog.CatalogSnapshot;
import java.util.Map;
import java.util.Set;

/**
 * What already exists in the database, so a configuration set (or a single uploaded document) can be validated
 * against the union of itself and stored state.
 *
 * @param bindingOwners {@code "sourceKey/externalRef" → employer key}
 */
public record ValidationContext(CatalogSnapshot catalog, Set<String> sourceKeys, Map<String, String> bindingOwners) {

    public ValidationContext {
        sourceKeys = Set.copyOf(sourceKeys);
        bindingOwners = Map.copyOf(bindingOwners);
    }

    public static ValidationContext empty() {
        return new ValidationContext(new CatalogSnapshot(Set.of(), Set.of(), Map.of()), Set.of(), Map.of());
    }
}
