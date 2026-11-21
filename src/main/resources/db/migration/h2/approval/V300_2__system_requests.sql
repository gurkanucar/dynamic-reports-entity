-- Requests from webhook and Kafka runs were opened "on behalf of" the flow owner, so an owner who is also a member
-- of the group could not decide them (four-eyes rule). No platform user asked for them: they now count as the
-- calling system's request, like new ones.
update approval_request
   set on_behalf_of = requested_by
 where status = 'PENDING'
   and (requested_by like 'webhook:%' or requested_by like 'kafka:%');
