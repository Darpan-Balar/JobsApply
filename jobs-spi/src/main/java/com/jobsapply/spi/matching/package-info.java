/**
 * Matching contracts. Hard eligibility ({@link com.jobsapply.spi.matching.EligibilityRule}) is kept separate from soft
 * ranking ({@link com.jobsapply.spi.matching.ScoreComponent}); LLM-backed components may only score, never decide
 * eligibility.
 */
package com.jobsapply.spi.matching;
