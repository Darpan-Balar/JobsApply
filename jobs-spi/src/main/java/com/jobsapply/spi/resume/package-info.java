/**
 * Reading a resume into a draft profile (ADR-0015). A {@link com.jobsapply.spi.resume.DocumentReader} turns a file
 * into text; a {@link com.jobsapply.spi.resume.ResumeExtractor} turns text into a {@link
 * com.jobsapply.spi.resume.ResumeExtraction} of what the resume literally says. Extractors never infer or invent:
 * anything not stated is {@code null}. The platform maps the result onto catalog skills, marks every fact unconfirmed,
 * and the candidate reviews it before anything is used.
 */
package com.jobsapply.spi.resume;
