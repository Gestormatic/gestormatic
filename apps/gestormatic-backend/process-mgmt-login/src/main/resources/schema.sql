create table if not exists users (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    auth_uid varchar(128) not null,
    email varchar(255),
    display_name varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (tenant_id, auth_uid)
);

create table if not exists roles (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    name varchar(64) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    unique (tenant_id, name)
);

create table if not exists user_roles (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    role_id bigint not null references roles(id) on delete cascade,
    unique (user_id, role_id)
);

create index if not exists idx_users_tenant_uid on users (tenant_id, auth_uid);
create index if not exists idx_roles_tenant_name on roles (tenant_id, name);
