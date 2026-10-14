package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.profile.FactRef;
import java.util.List;

/**
 * @param answer drafted answer, {@code null} unless {@code status == DRAFTED}
 * @param origin where a drafted answer comes from: verified facts (requires {@code sources}), the candidate's own
 *     contact details, or the candidate typing it
 */
public record AnswerDraft(String questionId, Status status, String answer, List<FactRef> sources, Origin origin, String note) {

    public AnswerDraft {
        sources = Lists.immutable(sources);
        origin = origin == null ? Origin.NONE : origin;
    }

    public enum Status {
        DRAFTED,
        NEEDS_USER_INPUT,
        NOT_APPLICABLE
    }

    public enum Origin {
        FACT,
        CONTACT,
        USER,
        NONE
    }

    public static AnswerDraft needsUser(String questionId, String note) {
        return new AnswerDraft(questionId, Status.NEEDS_USER_INPUT, null, List.of(), Origin.NONE, note);
    }
}
