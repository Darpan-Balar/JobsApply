-- Phase 8: tracking after submission (ADR-0013). Contacts, interviews and follow-ups are private to their owner;
-- names, contact details and notes are stored only inside encrypted envelopes ("details").

create table contact
(
    id            uuid primary key,
    owner_user_id uuid        not null references app_user (id) on delete cascade,
    employer_id   uuid references employer (id) on delete set null,
    details       jsonb       not null,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    version       bigint      not null,
    constraint uk_contact_id_owner unique (id, owner_user_id)
);
create index ix_contact_owner on contact (owner_user_id, created_at desc);

create table interview
(
    id               uuid primary key,
    owner_user_id    uuid        not null,
    application_id   uuid        not null,
    scheduled_at     timestamptz not null,
    duration_minutes integer,
    kind             varchar(16) not null,
    status           varchar(16) not null,
    details          jsonb       not null,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    version          bigint      not null,
    constraint fk_interview_application foreign key (application_id, owner_user_id)
        references application (id, owner_user_id) on delete cascade,
    constraint ck_interview_kind check (kind in ('PHONE_SCREEN', 'TECHNICAL', 'BEHAVIORAL', 'ONSITE', 'FINAL', 'OTHER')),
    constraint ck_interview_status check (status in ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    constraint ck_interview_duration check (duration_minutes is null or duration_minutes between 5 and 600)
);
create index ix_interview_owner_time on interview (owner_user_id, scheduled_at);
create index ix_interview_application on interview (application_id, scheduled_at);

create table follow_up
(
    id             uuid primary key,
    owner_user_id  uuid        not null,
    application_id uuid,
    contact_id     uuid,
    due_on         date        not null,
    done_at        timestamptz,
    details        jsonb       not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    version        bigint      not null,
    constraint fk_follow_up_owner foreign key (owner_user_id) references app_user (id) on delete cascade,
    constraint fk_follow_up_application foreign key (application_id, owner_user_id)
        references application (id, owner_user_id) on delete set null (application_id),
    constraint fk_follow_up_contact foreign key (contact_id, owner_user_id)
        references contact (id, owner_user_id) on delete set null (contact_id)
);
create index ix_follow_up_open on follow_up (owner_user_id, due_on) where done_at is null;
create index ix_follow_up_application on follow_up (application_id);
