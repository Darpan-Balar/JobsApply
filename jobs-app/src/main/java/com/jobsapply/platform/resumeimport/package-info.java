/**
 * Creating a candidate profile from a resume file (ADR-0015): read the file locally, extract what it states (locally,
 * or with Claude when the candidate opts in), map it onto the skill catalog, and return an unconfirmed draft for the
 * candidate to review and edit. Nothing is stored here: saving goes through the candidate module.
 */
package com.jobsapply.platform.resumeimport;
