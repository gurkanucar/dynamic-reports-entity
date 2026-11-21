-- Optimistic locking for the admin screens (ETag / If-Match).
alter table department add column version bigint default 0 not null;
