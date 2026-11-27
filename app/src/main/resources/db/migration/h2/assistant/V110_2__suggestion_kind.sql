-- One table for every kind of assistant request: datasets, mail templates, flows.
alter table dataset_suggestion add column kind varchar(20) default 'dataset' not null;
