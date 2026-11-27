-- Events carry more than 255 characters (e.g. a survey response with its answers).
alter table event_publication alter column serialized_event type text;
