-- Optimistic locking for the admin screens (ETag / If-Match).
alter table project add column version bigint default 0 not null;
alter table project_assignment add column version bigint default 0 not null;
