-- V2: eight sample tasks and their history. Dates are relative to the day the database is created, so a fresh
-- database always has one overdue task, one due today and three due within the week. No explicit ids: a fresh
-- database numbers the tasks 1 to 8 in insert order.

INSERT INTO "tasks" ("title", "description", "project", "status", "priority", "due_date",
                     "created_at", "updated_at", "completed_at") VALUES
  ('Publish the Vidocq 0.4.0 release notes',
   'Summarise the runtime, Mansart and dev console changes of the release.',
   'vidocq', 'OPEN', 'HIGH', DATEADD(DAY, 1, CURRENT_DATE),
   DATEADD(DAY, -6, CURRENT_TIMESTAMP), DATEADD(DAY, -6, CURRENT_TIMESTAMP), NULL),
  ('Review the dev console redaction rules',
   'Check that no password or credential-bearing URL reaches the console snapshot.',
   'vidocq', 'OPEN', 'MEDIUM', DATEADD(DAY, -2, CURRENT_DATE),
   DATEADD(DAY, -7, CURRENT_TIMESTAMP), DATEADD(DAY, -7, CURRENT_TIMESTAMP), NULL),
  ('Merge the MRTR batch pull request',
   'Give the batch interactions a last review, then merge.',
   'lc4jcdi', 'OPEN', 'HIGH', CURRENT_DATE,
   DATEADD(DAY, -4, CURRENT_TIMESTAMP), DATEADD(DAY, -4, CURRENT_TIMESTAMP), NULL),
  ('Write the mcp-tasks-server README',
   'Document the REST API, the MCP tools and the pool panel of the dev console.',
   'lc4jcdi', 'OPEN', 'MEDIUM', DATEADD(DAY, 5, CURRENT_DATE),
   DATEADD(DAY, -1, CURRENT_TIMESTAMP), DATEADD(DAY, -1, CURRENT_TIMESTAMP), NULL),
  ('Answer the H2 file-lock question',
   NULL,
   'lc4jcdi', 'OPEN', 'LOW', NULL,
   DATEADD(DAY, -2, CURRENT_TIMESTAMP), DATEADD(DAY, -2, CURRENT_TIMESTAMP), NULL),
  ('Book the car service',
   'The yearly service is due before winter.',
   'home', 'OPEN', 'LOW', DATEADD(DAY, 10, CURRENT_DATE),
   DATEADD(DAY, -3, CURRENT_TIMESTAMP), DATEADD(DAY, -3, CURRENT_TIMESTAMP), NULL),
  ('Renew the domain name',
   'Renew it for two years, then check the DNS records.',
   'home', 'DONE', 'HIGH', DATEADD(DAY, -5, CURRENT_DATE),
   DATEADD(DAY, -10, CURRENT_TIMESTAMP), DATEADD(DAY, -1, CURRENT_TIMESTAMP), DATEADD(DAY, -1, CURRENT_TIMESTAMP)),
  ('Tag the langchain4j-cdi snapshot build',
   NULL,
   'lc4jcdi', 'DONE', 'MEDIUM', NULL,
   DATEADD(DAY, -8, CURRENT_TIMESTAMP), DATEADD(DAY, -3, CURRENT_TIMESTAMP), DATEADD(DAY, -3, CURRENT_TIMESTAMP));

INSERT INTO "task_events" ("task_id", "type", "occurred_at", "detail")
  SELECT "id", 'CREATED', "created_at", 'seeded by V2' FROM "tasks" ORDER BY "id";
INSERT INTO "task_events" ("task_id", "type", "occurred_at", "detail")
  SELECT "id", 'COMPLETED', "completed_at", 'seeded by V2' FROM "tasks" WHERE "status" = 'DONE' ORDER BY "id";
