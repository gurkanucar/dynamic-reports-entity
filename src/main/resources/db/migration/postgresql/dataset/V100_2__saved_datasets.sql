-- Saved datasets are only named definitions, always computed live with the user's permissions. The background
-- snapshots (materialised copies refreshed every few hours) are gone: their rows move over, their tables are dropped.
create table saved_dataset (
        name varchar(40) not null,
        version bigint not null,
        label varchar(255),
        description varchar(1000),
        definition_json varchar(100000) not null,
        created_by varchar(255) not null,
        created_at timestamp(6) with time zone not null,
        updated_by varchar(255) not null,
        updated_at timestamp(6) with time zone not null,
        primary key (name)
    );

insert into saved_dataset (name, version, label, description, definition_json, created_by, created_at, updated_by,
                           updated_at)
select name, 0, label, description, definition_json, coalesce(created_by, 'system'),
       coalesce(created_at, current_timestamp), coalesce(updated_by, coalesce(created_by, 'system')),
       coalesce(updated_at, current_timestamp)
from dataset_snapshot;

-- materialised copies were tables named rds_<name>_<version>
do $$
declare t record;
begin
    for t in select tablename from pg_tables where schemaname = current_schema() and tablename like 'rds\_%' loop
        execute format('drop table if exists %I', t.tablename);
    end loop;
end $$;

drop table dataset_snapshot;
