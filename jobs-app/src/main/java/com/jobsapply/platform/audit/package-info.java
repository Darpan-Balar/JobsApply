/**
 * Append-only audit log. Domain events are recorded synchronously inside the transaction that caused them, so a change
 * and its audit record commit (or roll back) together. Payloads hold ids, statuses, codes and counts only.
 */
package com.jobsapply.platform.audit;
