/**
 * Source listings (one per source record, raw values preserved) and canonical job postings (normalized; several
 * listings of the same vacancy on different sources merge into one). Availability changes only on authoritative
 * evidence; a posting is "stale" when it has not been seen for {@code jobs.postings.stale-after} — stale is a flag,
 * never a closure.
 */
package com.jobsapply.platform.posting;
