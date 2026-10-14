package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.profile.FactRef;
import java.util.List;

/**
 * @param answer drafted answer, {@code null} unless {@code status == DRAFTED}
 * @param sources facts supporting the answer; required when {@code status == DRAFTED}
 */
public record AnswerDraft(String questionId, Status status, String answer, List<FactRef> sources, String note) {

    public AnswerDraft {
        sources = Lists.immutable(sources);
    }

    public enum Status {
        DRAFTED,
        NEEDS_USER_INPUT,
        NOT_APPLICABLE
    }
}
