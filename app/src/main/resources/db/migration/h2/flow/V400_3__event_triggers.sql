-- Event triggers: flows started by events of other modules (e.g. survey responses).
alter table flow_trigger alter column trigger_type set data type enum ('EVENT','KAFKA','WEBHOOK');
alter table flow_run alter column trigger_type set data type enum ('EVENT','KAFKA','MANUAL','SCHEDULE','WEBHOOK');
