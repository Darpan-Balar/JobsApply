package com.jobsapply.spi.application;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.profile.FactRef;
import java.util.List;
import java.util.Objects;

/** Values to place into form fields, decided by the platform. Adapters must fill only {@link Confidence#HIGH} mappings. */
public record FieldMappingPlan(List<FieldMapping> mappings) {

    public FieldMappingPlan {
        mappings = Lists.immutable(mappings);
    }

    /**
     * @param value text value (for selects and radios: the option label to choose), or {@code null} for file fields
     * @param attachment the file for file fields, else {@code null}
     * @param origin why this value belongs here (shown to the human; never contains the value itself)
     */
    public record FieldMapping(String fieldId, String value, Attachment attachment, Confidence confidence, List<FactRef> sources,
            String origin) {

        public FieldMapping {
            Objects.requireNonNull(fieldId, "fieldId");
            Objects.requireNonNull(confidence, "confidence");
            sources = Lists.immutable(sources);
        }

        @Override
        public String toString() {
            return "FieldMapping[" + fieldId + ", " + confidence + ", redacted]";
        }
    }

    /** A generated document to upload, e.g. the resume PDF. */
    public record Attachment(String documentRef, String fileName, String mediaType, byte[] content) {

        public Attachment {
            Objects.requireNonNull(content, "content");
        }

        @Override
        public String toString() {
            return "Attachment[" + documentRef + ", " + mediaType + ", " + content.length + " bytes]";
        }
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }
}
