-- Shared catalog: job families and skills with synonym aliases.

create table job_family
(
    id           uuid primary key,
    key          varchar(63)  not null,
    display_name varchar(200) not null,
    description  text,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    version      bigint       not null,
    constraint uk_job_family_key unique (key),
    constraint ck_job_family_key check (key ~ '^[a-z0-9][a-z0-9-]{0,62}$')
);

create table skill
(
    id           uuid primary key,
    key          varchar(63)  not null,
    display_name varchar(200) not null,
    category     varchar(63),
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    version      bigint       not null,
    constraint uk_skill_key unique (key),
    constraint ck_skill_key check (key ~ '^[a-z0-9][a-z0-9-]{0,62}$')
);

-- An alias resolves to exactly one skill (globally unique).
create table skill_alias
(
    id               uuid primary key,
    skill_id         uuid         not null references skill (id) on delete cascade,
    alias_normalized varchar(100) not null,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null,
    constraint uk_skill_alias_alias unique (alias_normalized),
    constraint ck_skill_alias_normalized check (alias_normalized = lower(btrim(alias_normalized)) and alias_normalized <> '')
);
create index ix_skill_alias_skill on skill_alias (skill_id);
