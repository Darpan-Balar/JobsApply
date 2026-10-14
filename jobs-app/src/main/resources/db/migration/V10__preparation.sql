-- Phase 5: application preparation. Packages, documents and master resumes are immutable (triggers); personal content
-- is encrypted by the application (package payload) or stored as encrypted files (documents, master resumes).

create table resume_template
(
    id            uuid primary key,
    owner_user_id uuid references app_user (id) on delete cascade,
    key           varchar(63)  not null,
    display_name  varchar(200) not null,
    definition    jsonb        not null,
    content_hash  varchar(64)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       bigint       not null,
    constraint uk_resume_template_owner_key unique nulls not distinct (owner_user_id, key),
    constraint ck_resume_template_key check (key ~ '^[a-z0-9][a-z0-9-]{0,62}$'),
    constraint ck_resume_template_definition check (jsonb_typeof(definition) = 'object')
);

-- The candidate's own resume file, kept exactly as uploaded (each upload is a new immutable row).
create table master_resume
(
    id                   uuid primary key,
    owner_user_id        uuid         not null,
    candidate_profile_id uuid         not null,
    file_name            varchar(255) not null,
    media_type           varchar(100) not null,
    size_bytes           bigint       not null,
    sha256               varchar(64)  not null,
    storage_key          varchar(100) not null,
    uploaded_at          timestamptz  not null,
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    version              bigint       not null,
    constraint fk_master_resume_profile foreign key (candidate_profile_id, owner_user_id)
        references candidate_profile (id, owner_user_id) on delete cascade,
    constraint uk_master_resume_storage_key unique (storage_key),
    constraint ck_master_resume_size check (size_bytes > 0)
);
create index ix_master_resume_profile on master_resume (candidate_profile_id, uploaded_at desc);
create trigger trg_master_resume_immutable before update on master_resume for each row execute function forbid_mutation();

alter table application add constraint uk_application_id_owner unique (id, owner_user_id);

create table application_package
(
    id              uuid primary key,
    owner_user_id   uuid        not null,
    application_id  uuid        not null,
    version_no      integer     not null,
    payload         jsonb       not null,
    content_hash    varchar(64) not null,
    blocking_issues integer     not null,
    warnings        integer     not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint      not null,
    constraint fk_application_package_application foreign key (application_id, owner_user_id)
        references application (id, owner_user_id) on delete cascade,
    constraint uk_application_package_version unique (application_id, version_no),
    constraint uk_application_package_id_owner unique (id, owner_user_id),
    constraint uk_application_package_id_application unique (id, application_id),
    constraint ck_application_package_version check (version_no > 0),
    constraint ck_application_package_counts check (blocking_issues >= 0 and warnings >= 0)
);
create trigger trg_application_package_immutable before update on application_package for each row execute function forbid_mutation();

create table application_document
(
    id            uuid primary key,
    owner_user_id uuid         not null,
    package_id    uuid         not null,
    kind          varchar(16)  not null,
    media_type    varchar(100) not null,
    size_bytes    bigint       not null,
    sha256        varchar(64)  not null,
    storage_key   varchar(100) not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       bigint       not null,
    constraint fk_application_document_package foreign key (package_id, owner_user_id)
        references application_package (id, owner_user_id) on delete cascade,
    constraint ck_application_document_kind check (kind in ('RESUME', 'COVER_NOTE')),
    constraint ck_application_document_size check (size_bytes > 0)
);
create index ix_application_document_package on application_document (package_id);
create trigger trg_application_document_immutable before update on application_document for each row execute function forbid_mutation();

-- The package currently under review; it must belong to this application.
alter table application add column current_package_id uuid;
alter table application add constraint fk_application_current_package
    foreign key (current_package_id, id) references application_package (id, application_id)
        on delete set null (current_package_id) deferrable initially deferred;
