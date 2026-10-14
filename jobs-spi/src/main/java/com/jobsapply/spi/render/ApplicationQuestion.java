package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import java.util.List;

public record ApplicationQuestion(String id, String text, QuestionType type, boolean required, List<String> options) {

    public ApplicationQuestion {
        options = Lists.immutable(options);
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
}
