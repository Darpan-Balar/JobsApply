/**
 * Matching: evaluates postings for the current user's candidate profile against a role profile with the configured
 * strategy plugin, and stores explainable, idempotent results. Works without any LLM; hard eligibility is separate
 * from soft scoring, and unknown or unverified facts are never treated as a pass.
 */
package com.jobsapply.platform.matching;
