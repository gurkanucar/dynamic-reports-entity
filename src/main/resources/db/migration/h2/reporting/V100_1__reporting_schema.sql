-- Module 'reporting': its own tables only, no foreign keys into other modules.
-- Versions 100.x belong to this module (generated once from the JPA mapping, owned here since).

create table report_dataset (
        refresh_every_hours integer,
        created_at timestamp(6) with time zone,
        next_refresh_at timestamp(6) with time zone,
        refresh_lease_until timestamp(6) with time zone,
        refresh_millis bigint,
        refreshed_at timestamp(6) with time zone,
        row_count bigint,
        snapshot_version bigint not null,
        updated_at timestamp(6) with time zone,
        version bigint not null,
        name varchar(40) not null,
        table_name varchar(80),
        refresh_owner varchar(200),
        description varchar(1000),
        last_error varchar(2000),
        columns_json varchar(100000),
        definition_json varchar(100000) not null,
        created_by varchar(255),
        label varchar(255),
        updated_by varchar(255),
        dataset_mode enum ('LIVE','SNAPSHOT') not null,
        status enum ('FAILED','NEW','READY','REFRESHING') not null,
        primary key (name)
    );
