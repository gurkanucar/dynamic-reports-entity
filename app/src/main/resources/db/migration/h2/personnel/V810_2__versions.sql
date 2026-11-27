-- Optimistic locking for the admin screens (ETag / If-Match).
alter table personnel add column version bigint default 0 not null;
alter table skill add column version bigint default 0 not null;
