-- =====================================================================================
-- Bulk demo data, loaded after data.sql by default (skip it with the "small" profile):
--   ./mvnw spring-boot:run -Dspring-boot.run.profiles=small
-- Generated with H2's SYSTEM_RANGE (H2-specific, like the rest of the demo data):
--   50 teams, 100,000 personnel, 90,000 profiles, ~233,000 skill links,
--   ~228,000 training records, 2,000 projects, 125,000 project assignments.
-- Bulk people have no login; use admin (ALL_DEPARTMENTS) to see them.
-- =====================================================================================

-- ---- 50 teams under the existing departments; team X is led by bulk person X --------
INSERT INTO department (id, name, cost_center, parent_id, manager_id)
SELECT 100 + X,
       'Team ' || CAST(X AS VARCHAR),
       'CC-' || CAST(1000 + X AS VARCHAR),
       2 + MOD(X, 6),
       1000 + X
FROM SYSTEM_RANGE(1, 50);

-- ---- 100,000 personnel; the first 50 lead a team, everyone else reports to their lead --
INSERT INTO personnel (id, username, full_name, email, hire_date, active, salary, national_id, department_id, manager_id)
SELECT 1000 + X,
       'user' || CAST(X AS VARCHAR),
       CASE MOD(X, 10)
           WHEN 0 THEN 'Alex' WHEN 1 THEN 'Sam' WHEN 2 THEN 'Jordan' WHEN 3 THEN 'Taylor' WHEN 4 THEN 'Morgan'
           WHEN 5 THEN 'Casey' WHEN 6 THEN 'Riley' WHEN 7 THEN 'Jamie' WHEN 8 THEN 'Avery' ELSE 'Cameron' END
           || ' ' ||
       CASE MOD(X / 10, 10)
           WHEN 0 THEN 'Yilmaz' WHEN 1 THEN 'Kaya' WHEN 2 THEN 'Demir' WHEN 3 THEN 'Sahin' WHEN 4 THEN 'Celik'
           WHEN 5 THEN 'Brown' WHEN 6 THEN 'Taylor' WHEN 7 THEN 'Martin' WHEN 8 THEN 'Lopez' ELSE 'Nguyen' END,
       'user' || CAST(X AS VARCHAR) || '@example.com',
       DATEADD('DAY', -MOD(X * 37, 6000), CURRENT_DATE),
       MOD(X, 17) <> 0,
       CAST(30000 + MOD(X * 7919, 120000) AS DECIMAL(19, 2)),
       'B' || CAST(X AS VARCHAR),
       101 + MOD(X - 1, 50),
       CASE WHEN X <= 50 THEN 1 ELSE 1001 + MOD(X - 1, 50) END
FROM SYSTEM_RANGE(1, 100000);

-- ---- One-to-one profiles for 90% of them ----------------------------------------------
INSERT INTO personnel_profile (id, personnel_id, birth_date, blood_type, city, emergency_contact)
SELECT 1000 + X,
       1000 + X,
       DATEADD('DAY', -(8000 + MOD(X * 53, 12000)), CURRENT_DATE),
       CASE MOD(X, 8)
           WHEN 0 THEN 'A+' WHEN 1 THEN 'A-' WHEN 2 THEN 'B+' WHEN 3 THEN 'B-'
           WHEN 4 THEN 'AB+' WHEN 5 THEN 'AB-' WHEN 6 THEN 'O+' ELSE 'O-' END,
       CASE MOD(X, 6)
           WHEN 0 THEN 'Istanbul' WHEN 1 THEN 'Ankara' WHEN 2 THEN 'Izmir'
           WHEN 3 THEN 'Bursa' WHEN 4 THEN 'Antalya' ELSE 'Konya' END,
       'Contact ' || CAST(X AS VARCHAR)
FROM SYSTEM_RANGE(1, 100000)
WHERE MOD(X, 10) <> 0;

-- ---- Pure many-to-many: 2 skills each, a third for every third person -----------------
INSERT INTO personnel_skill (personnel_id, skill_id)
SELECT 1000 + X, 1 + MOD(X, 10) FROM SYSTEM_RANGE(1, 100000)
UNION ALL
SELECT 1000 + X, 1 + MOD(X + 3, 10) FROM SYSTEM_RANGE(1, 100000)
UNION ALL
SELECT 1000 + X, 1 + MOD(X + 7, 10) FROM SYSTEM_RANGE(1, 100000) WHERE MOD(X, 3) = 0;

-- ---- Many-to-many with fields: training records ---------------------------------------
-- Occupational Health and Safety for everyone; renewal between 60 days overdue and ~2 years ahead
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + X, 1,
       DATEADD('DAY', MOD(X * 13, 730) - 60 - 365, CURRENT_DATE),
       DATEADD('DAY', MOD(X * 13, 730) - 60, CURRENT_DATE),
       60 + MOD(X * 7, 41), 'PASSED'
FROM SYSTEM_RANGE(1, 100000);

-- Data Protection for every second person
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + X, 3,
       DATEADD('DAY', MOD(X * 29, 900) - 100 - 730, CURRENT_DATE),
       DATEADD('DAY', MOD(X * 29, 900) - 100, CURRENT_DATE),
       55 + MOD(X * 11, 46), 'PASSED'
FROM SYSTEM_RANGE(1, 100000) WHERE MOD(X, 2) = 0;

-- Fire Safety for every third person
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + X, 2,
       DATEADD('DAY', MOD(X * 31, 400) - 30 - 365, CURRENT_DATE),
       DATEADD('DAY', MOD(X * 31, 400) - 30, CURRENT_DATE),
       65 + MOD(X * 3, 36), 'PASSED'
FROM SYSTEM_RANGE(1, 100000) WHERE MOD(X, 3) = 0;

-- First Aid for every fourth person
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + X, 5,
       DATEADD('DAY', MOD(X * 41, 1100) - 1095, CURRENT_DATE),
       DATEADD('DAY', MOD(X * 41, 1100), CURRENT_DATE),
       70 + MOD(X * 5, 31), 'PASSED'
FROM SYSTEM_RANGE(1, 100000) WHERE MOD(X, 4) = 0;

-- Secure Coding for every fifth person; one in five of them failed (no renewal date)
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status)
SELECT 1000 + X, 6,
       DATEADD('DAY', MOD(X * 19, 365) - 365, CURRENT_DATE),
       CASE WHEN MOD(X, 25) = 0 THEN NULL ELSE DATEADD('DAY', MOD(X * 19, 365), CURRENT_DATE) END,
       CASE WHEN MOD(X, 25) = 0 THEN 30 + MOD(X, 25) ELSE 70 + MOD(X * 13, 31) END,
       CASE WHEN MOD(X, 25) = 0 THEN 'FAILED' ELSE 'PASSED' END
FROM SYSTEM_RANGE(1, 100000) WHERE MOD(X, 5) = 0;

-- ---- 2,000 projects owned by the teams -------------------------------------------------
INSERT INTO project (id, code, name, status, budget, start_date, end_date, department_id)
SELECT 1000 + X,
       'PRJ-' || LPAD(CAST(X AS VARCHAR), 5, '0'),
       'Project ' || CAST(X AS VARCHAR),
       CASE MOD(X, 4) WHEN 0 THEN 'ACTIVE' WHEN 1 THEN 'PLANNED' WHEN 2 THEN 'COMPLETED' ELSE 'ON_HOLD' END,
       CAST(10000 + MOD(X * 4999, 990000) AS DECIMAL(19, 2)),
       DATEADD('DAY', -MOD(X * 17, 1500), CURRENT_DATE),
       CASE WHEN MOD(X, 4) = 2 THEN DATEADD('DAY', 180 - MOD(X * 17, 1500), CURRENT_DATE) END,
       101 + MOD(X, 50)
FROM SYSTEM_RANGE(1, 2000);

-- ---- Many-to-many with fields across modules: one project each, a second for every fourth person
INSERT INTO project_assignment (project_id, personnel_id, role, allocation_percent, assigned_on)
SELECT 1001 + MOD(X, 2000), 1000 + X,
       CASE MOD(X, 5) WHEN 0 THEN 'Lead' WHEN 1 THEN 'Developer' WHEN 2 THEN 'Analyst' WHEN 3 THEN 'Engineer' ELSE 'Tester' END,
       20 + 10 * MOD(X, 9),
       DATEADD('DAY', -MOD(X * 11, 1200), CURRENT_DATE)
FROM SYSTEM_RANGE(1, 100000)
UNION ALL
SELECT 1001 + MOD(X * 7 + 1, 2000), 1000 + X, 'Reviewer', 10,
       DATEADD('DAY', -MOD(X * 19, 1200), CURRENT_DATE)
FROM SYSTEM_RANGE(1, 100000) WHERE MOD(X, 4) = 0;

-- ---- Let generated ids continue after the bulk rows ------------------------------------
ALTER TABLE department         ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE personnel          ALTER COLUMN id RESTART WITH 200000;
ALTER TABLE personnel_profile  ALTER COLUMN id RESTART WITH 200000;
ALTER TABLE project            ALTER COLUMN id RESTART WITH 10000;
