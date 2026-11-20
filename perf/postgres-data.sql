-- =====================================================================================
-- Bulk data for PostgreSQL, a scaled-up version of src/main/resources/data-bulk.sql.
-- Run by perf/load-postgres.sh after the demo data (data.sql); psql variable "scale" = personnel
-- count (default 1,000,000). With the default:
--   500 teams, 1M personnel, 900k profiles, ~2.33M skill links, ~2.28M training records,
--   20k projects, 1.25M project assignments  (~7.8M rows)
-- =====================================================================================
\if :{?scale}
\else
  \set scale 1000000
\endif
SELECT :scale / 2000 AS teams, :scale / 50 AS projects \gset
\timing on

-- ---- teams under the existing departments; team X is led by bulk person X -----------
INSERT INTO department (id, name, cost_center, parent_id, manager_id)
SELECT 100 + x, 'Team ' || x, 'CC-' || (1000 + x), 2 + x % 6, 1000 + x
FROM generate_series(1, :teams) AS g(x);

-- ---- personnel; the first :teams lead a team, everyone else reports to their lead -----
INSERT INTO personnel (id, username, full_name, email, hire_date, active, salary, national_id, department_id, manager_id)
SELECT 1000 + x,
       'user' || x,
       (ARRAY['Alex','Sam','Jordan','Taylor','Morgan','Casey','Riley','Jamie','Avery','Cameron'])[1 + x % 10]
           || ' ' ||
       (ARRAY['Yilmaz','Kaya','Demir','Sahin','Celik','Brown','Taylor','Martin','Lopez','Nguyen'])[1 + (x / 10) % 10],
       'user' || x || '@example.com',
       CURRENT_DATE - (x * 37 % 6000)::int,
       x % 17 <> 0,
       (30000 + x * 7919 % 120000)::numeric(38, 2),
       'B' || x,
       101 + (x - 1) % :teams,
       CASE WHEN x <= :teams THEN 1 ELSE 1001 + (x - 1) % :teams END
FROM generate_series(1::bigint, :scale) AS g(x);

-- ---- one-to-one profiles for 90% of them ----------------------------------------------
INSERT INTO personnel_profile (id, personnel_id, birth_date, blood_type, city, emergency_contact)
SELECT 1000 + x, 1000 + x,
       CURRENT_DATE - (8000 + x * 53 % 12000)::int,
       (ARRAY['A+','A-','B+','B-','AB+','AB-','O+','O-'])[1 + x % 8],
       (ARRAY['Istanbul','Ankara','Izmir','Bursa','Antalya','Konya'])[1 + x % 6],
       'Contact ' || x
FROM generate_series(1::bigint, :scale) AS g(x)
WHERE x % 10 <> 0;

-- ---- pure many-to-many: 2 skills each, a third for every third person -----------------
INSERT INTO personnel_skill (personnel_id, skill_id)
SELECT 1000 + x, 1 + x % 10 FROM generate_series(1::bigint, :scale) AS g(x)
UNION ALL
SELECT 1000 + x, 1 + (x + 3) % 10 FROM generate_series(1::bigint, :scale) AS g(x)
UNION ALL
SELECT 1000 + x, 1 + (x + 7) % 10 FROM generate_series(1::bigint, :scale) AS g(x) WHERE x % 3 = 0;

-- ---- many-to-many with fields: training records ---------------------------------------
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + x, 1,
       CURRENT_DATE + (x * 13 % 730 - 60 - 365)::int,
       CURRENT_DATE + (x * 13 % 730 - 60)::int,
       60 + x * 7 % 41, 'PASSED'
FROM generate_series(1::bigint, :scale) AS g(x);

INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + x, 3,
       CURRENT_DATE + (x * 29 % 900 - 100 - 730)::int,
       CURRENT_DATE + (x * 29 % 900 - 100)::int,
       55 + x * 11 % 46, 'PASSED'
FROM generate_series(1::bigint, :scale) AS g(x) WHERE x % 2 = 0;

INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + x, 2,
       CURRENT_DATE + (x * 31 % 400 - 30 - 365)::int,
       CURRENT_DATE + (x * 31 % 400 - 30)::int,
       65 + x * 3 % 36, 'PASSED'
FROM generate_series(1::bigint, :scale) AS g(x) WHERE x % 3 = 0;

INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + x, 5,
       CURRENT_DATE + (x * 41 % 1100 - 1095)::int,
       CURRENT_DATE + (x * 41 % 1100)::int,
       70 + x * 5 % 31, 'PASSED'
FROM generate_series(1::bigint, :scale) AS g(x) WHERE x % 4 = 0;

INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + x, 6,
       CURRENT_DATE + (x * 19 % 365 - 365)::int,
       CASE WHEN x % 25 = 0 THEN NULL ELSE CURRENT_DATE + (x * 19 % 365)::int END,
       CASE WHEN x % 25 = 0 THEN 30 + x % 25 ELSE 70 + x * 13 % 31 END,
       CASE WHEN x % 25 = 0 THEN 'FAILED' ELSE 'PASSED' END
FROM generate_series(1::bigint, :scale) AS g(x) WHERE x % 5 = 0;

-- ---- projects owned by the teams --------------------------------------------------------
INSERT INTO project (id, code, name, status, budget, start_date, end_date, department_id)
SELECT 1000 + x,
       'PRJ-' || lpad(x::text, 6, '0'),
       'Project ' || x,
       (ARRAY['ACTIVE','PLANNED','COMPLETED','ON_HOLD'])[1 + x % 4],
       (10000 + x * 4999 % 990000)::numeric(38, 2),
       CURRENT_DATE - (x * 17 % 1500)::int,
       CASE WHEN x % 4 = 2 THEN CURRENT_DATE + (180 - x * 17 % 1500)::int END,
       101 + x % :teams
FROM generate_series(1::bigint, :projects) AS g(x);

-- ---- project assignments: one project each, a second for every fourth person -----------
INSERT INTO project_assignment (project_id, personnel_id, role, allocation_percent, assigned_on)
SELECT 1001 + x % :projects, 1000 + x,
       (ARRAY['Lead','Developer','Analyst','Engineer','Tester'])[1 + x % 5],
       20 + 10 * (x % 9),
       CURRENT_DATE - (x * 11 % 1200)::int
FROM generate_series(1::bigint, :scale) AS g(x)
UNION ALL
SELECT 1001 + (x * 7 + 1) % :projects, 1000 + x, 'Reviewer', 10,
       CURRENT_DATE - (x * 19 % 1200)::int
FROM generate_series(1::bigint, :scale) AS g(x) WHERE x % 4 = 0;

-- ---- let generated ids continue after the bulk rows -------------------------------------
SELECT setval(pg_get_serial_sequence('department', 'id'), (SELECT max(id) FROM department));
SELECT setval(pg_get_serial_sequence('personnel', 'id'), (SELECT max(id) FROM personnel));
SELECT setval(pg_get_serial_sequence('personnel_profile', 'id'), (SELECT max(id) FROM personnel_profile));
SELECT setval(pg_get_serial_sequence('project', 'id'), (SELECT max(id) FROM project));

-- ---- fresh statistics for the planner ---------------------------------------------------
VACUUM ANALYZE;
