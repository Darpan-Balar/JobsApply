-- Phase 4: matching.
-- Skills whose key or name is an ordinary word (e.g. "go", "c") are extracted from job text only via their aliases.
alter table skill add column extract_aliases_only boolean not null default false;

-- Listing a user's matches for one candidate version and role version, best first.
create index ix_job_match_inputs_score on job_match (owner_user_id, candidate_profile_version_id, role_profile_version_id, score desc);
