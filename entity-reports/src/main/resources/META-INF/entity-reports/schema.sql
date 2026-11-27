-- entity-reports: saved datasets (H2 and PostgreSQL). Copy into a Flyway/Liquibase migration, or let the library
-- create it with entity-reports.saved.initialize-schema=true.
create table if not exists saved_dataset (
    id              uuid                        not null,
    name            varchar(40)                 not null,
    version         bigint                      not null,
    label           varchar(255),
    description     varchar(1000),
    definition_json varchar(100000)             not null,
    created_by      varchar(255)                not null,
    created_at      timestamp(6) with time zone not null,
    updated_by      varchar(255)                not null,
    updated_at      timestamp(6) with time zone not null,
    constraint pk_saved_dataset primary key (id),
    constraint uk_saved_dataset_name unique (name)
);
