-- Event triggers: flows started by events of other modules (e.g. survey responses).
alter table flow_trigger drop constraint if exists flow_trigger_trigger_type_check;
alter table flow_trigger add constraint flow_trigger_trigger_type_check
    check (trigger_type in ('WEBHOOK','KAFKA','EVENT'));
alter table flow_run drop constraint if exists flow_run_trigger_type_check;
alter table flow_run add constraint flow_run_trigger_type_check
    check (trigger_type in ('MANUAL','SCHEDULE','WEBHOOK','KAFKA','EVENT'));
