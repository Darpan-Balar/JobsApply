package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import java.util.List;

/**
 * A question on an application form.
 *
 * @param kind STANDARD fields (name, e-mail, resume, ...) are filled from contact details and generated documents;
 *     SELF_IDENTIFICATION (voluntary demographic) questions are never answered automatically
 */
public record ApplicationQuestion(String id, String text, QuestionType type, boolean required, List<String> options, Kind kind) {

    public ApplicationQuestion {
        options = Lists.immutable(options);
        kind = kind == null ? Kind.SCREENING : kind;
    }

    public ApplicationQuestion(String id, String text, QuestionType type, boolean required, List<String> options) {
        this(id, text, type, required, options, Kind.SCREENING);
    }

    public enum QuestionType {
        TEXT,
        TEXTAREA,
        SINGLE_SELECT,
        MULTI_SELECT,
        BOOLEAN,
        NUMBER,
        DATE,
        FILE
    }

    public enum Kind {
        STANDARD,
        SCREENING,
        SELF_IDENTIFICATION
    }
}
