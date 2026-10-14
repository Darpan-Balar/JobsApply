-- Phase 2: employer registry. A user's private stance on an employer (ADR-0002: user-owned).

create table user_employer_preference
(
    id            uuid primary key,
    owner_user_id uuid        not null references app_user (id) on delete cascade,
    employer_id   uuid        not null references employer (id),
    mode          varchar(8)  not null,
    priority      smallint    not null default 50,
    note          varchar(1000),
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    version       bigint      not null,
    constraint uk_user_employer_preference unique (owner_user_id, employer_id),
    constraint ck_user_employer_preference_mode check (mode in ('WATCH', 'IGNORE')),
    constraint ck_user_employer_preference_priority check (priority between 0 and 100)
);
create index ix_user_employer_preference_employer on user_employer_preference (employer_id);

-- Tags are key-format slugs (max 63 chars). varchar[] matches the parameter type Hibernate binds for array
-- predicates (text[] @> varchar[] has no operator), so tag filters run in the database and use the GIN index.
drop index ix_employer_tags;
alter table employer alter column tags type varchar(63)[];
alter table employer alter column tags set default '{}';
create index ix_employer_tags on employer using gin (tags);
