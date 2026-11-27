-- Optimistic locking for the admin screens (ETag / If-Match).
alter table training add column version bigint default 0 not null;
alter table training_record add column version bigint default 0 not null;
