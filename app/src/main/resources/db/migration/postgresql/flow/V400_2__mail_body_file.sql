-- The sent HTML body is kept as a run file (flow_artifact, same retention as other files); null for older mails.
alter table flow_mail_delivery add column body_file_id bigint;
