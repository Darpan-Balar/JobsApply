-- Phase 9: Spring Modulith event publication registry (transactional outbox, ADR-0014). The DDL is Modulith 2.1's own
-- v2 PostgreSQL schema (spring-modulith-events-jdbc: schemas/v2/schema-postgresql.sql), owned here by Flyway.
-- Events carry ids, statuses and codes only (never personal data), so serialized_event holds nothing sensitive.
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
