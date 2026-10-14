-- Phase 3: discovery.
-- The source's description HTML is stored as received (sanitized only when rendered); job_posting keeps plain text.
alter table job_listing add column description_html text;

-- Availability bookkeeping per binding (closing listings absent from a complete listing, rechecks).
create index ix_job_listing_binding_availability on job_listing (employer_source_id, availability);
