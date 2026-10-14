/**
 * Built-in matching plugins: eligibility rules (hard constraints), score components (soft preferences) and the
 * {@code weighted} strategy. Every plugin works only from {@link com.jobsapply.spi.matching.MatchContext}; missing or
 * unverified facts yield {@code UNKNOWN} / no score — never a pass or a fail.
 */
package com.jobsapply.adapters.matching;
