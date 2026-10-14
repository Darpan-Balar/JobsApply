package com.jobsapply.spi.application;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.profile.FactRef;
import java.util.List;

/** Values to place into form fields. Adapters must fill only {@link Confidence#HIGH} mappings. */
public record FieldMappingPlan(List<FieldMapping> mappings) {

    public FieldMappingPlan {
        mappings = Lists.immutable(mappings);
    }

    /**
     * @param value text value, or {@code null} for file fields
     * @param documentRef platform reference to a generated document for file fields, else {@code null}
     */
    public record FieldMapping(
            String fieldId, String value, String documentRef, Confidence confidence, List<FactRef> sources) {
        public FieldMapping {
            sources = Lists.immutable(sources);
        }
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }
}
