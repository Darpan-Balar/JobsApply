package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.matching.MatchContext;

/**
 * Drafts screening-question answers. Must return {@code NEEDS_USER_INPUT} rather than guess, in particular for legal
 * eligibility, work authorization, compensation, notice period and referrals.
 */
public interface AnswerDrafter extends Plugin {

    AnswerDraft draft(ApplicationQuestion question, MatchContext context);
}
