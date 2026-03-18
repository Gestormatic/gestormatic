create schema if not exists procedures;

create table if not exists procedures.procedure_templates (
    id          bigserial primary key,
    tenant_id   varchar(64)  not null,
    name        varchar(255) not null,
    description text,
    status      varchar(32)  not null default 'DRAFT',
    created_by  varchar(128) not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create table if not exists procedures.procedure_steps (
    id          bigserial primary key,
    template_id bigint       not null references procedures.procedure_templates(id) on delete cascade,
    step_order  integer      not null,
    name        varchar(255) not null,
    description text,
    unique (template_id, step_order)
);

create table if not exists procedures.required_documents (
    id           bigserial primary key,
    step_id      bigint       not null references procedures.procedure_steps(id) on delete cascade,
    name         varchar(255) not null,
    description  text,
    is_mandatory boolean      not null default true
);

create table if not exists procedures.procedure_members (
    id          bigserial primary key,
    template_id bigint       not null references procedures.procedure_templates(id) on delete cascade,
    user_id     varchar(128) not null,
    member_role varchar(32)  not null,
    unique (template_id, user_id),
    constraint chk_member_role check (member_role in ('OWNER', 'CUSTOMER'))
);

create index if not exists idx_templates_tenant     on procedures.procedure_templates (tenant_id);
create index if not exists idx_steps_template       on procedures.procedure_steps (template_id);
create index if not exists idx_documents_step       on procedures.required_documents (step_id);
create index if not exists idx_members_template     on procedures.procedure_members (template_id);
create index if not exists idx_members_user         on procedures.procedure_members (user_id);

-- Bloque B: trámites concretos (instancias de un tipo de trámite)
create table if not exists procedures.procedure_cases (
    id          bigserial    primary key,
    tenant_id   varchar(64)  not null,
    template_id bigint       not null references procedures.procedure_templates(id),
    customer_id varchar(128) not null,
    assigned_to varchar(128),
    status      varchar(32)  not null default 'RECEIVED',
    notes       text,
    created_by  varchar(128) not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint chk_case_status check (
        status in ('RECEIVED','IN_REVIEW','PENDING_DOCS','APPROVED','REJECTED')
    )
);

create index if not exists idx_cases_tenant     on procedures.procedure_cases (tenant_id);
create index if not exists idx_cases_template   on procedures.procedure_cases (template_id);
create index if not exists idx_cases_customer   on procedures.procedure_cases (customer_id);
create index if not exists idx_cases_assigned   on procedures.procedure_cases (assigned_to);
create index if not exists idx_cases_status     on procedures.procedure_cases (status);
create index if not exists idx_cases_created_at on procedures.procedure_cases (created_at);
