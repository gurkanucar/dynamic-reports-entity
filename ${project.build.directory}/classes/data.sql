-- =====================================================================================
-- Demo data. Renewal dates are relative to today so date filters always have hits.
-- Explicit ids keep the references readable; identities are restarted at the end.
-- Login users (password "password"): ceo, alice, bob, carol, ... = personnel.username
-- =====================================================================================

-- ---- Organization: department hierarchy (self-reference via parent_id) -------------
INSERT INTO department (id, name, cost_center, parent_id) VALUES
    (1, 'Head Office',            'CC-100', NULL),
    (2, 'Human Resources',        'CC-200', 1),
    (3, 'Information Technology', 'CC-300', 1),
    (4, 'Software Development',   'CC-310', 3),
    (5, 'Infrastructure',         'CC-320', 3),
    (6, 'Finance',                'CC-400', 1),
    (7, 'Sales',                  'CC-500', 1);

-- ---- Personnel (many-to-one department, self-reference manager) ----------------------
INSERT INTO personnel (id, username, full_name, email, hire_date, active, salary, national_id, department_id, manager_id) VALUES
    ( 1, 'ceo',    'Diana Prince', 'diana.prince@example.com', DATE '2012-03-01', TRUE,  150000.00, '10000000001', 1, NULL),
    ( 2, 'alice',  'Alice Smith',  'alice.smith@example.com',  DATE '2016-05-10', TRUE,   72000.00, '10000000002', 2, 1),
    ( 3, 'hannah', 'Hannah Lee',   'hannah.lee@example.com',   DATE '2021-09-01', TRUE,   48000.00, '10000000003', 2, 2),
    ( 4, 'bob',    'Bob Jones',    'bob.jones@example.com',    DATE '2014-01-15', TRUE,   95000.00, '10000000004', 3, 1),
    ( 5, 'carol',  'Carol White',  'carol.white@example.com',  DATE '2018-07-02', TRUE,   82000.00, '10000000005', 4, 4),
    ( 6, 'dave',   'Dave Miller',  'dave.miller@example.com',  DATE '2020-02-17', TRUE,   68000.00, '10000000006', 4, 5),
    ( 7, 'erin',   'Erin Garcia',  'erin.garcia@example.com',  DATE '2022-11-07', TRUE,   61000.00, '10000000007', 4, 5),
    ( 8, 'frank',  'Frank Wilson', 'frank.wilson@example.com', DATE '2023-04-03', TRUE,   57000.00, '10000000008', 4, 5),
    ( 9, 'grace',  'Grace Kim',    'grace.kim@example.com',    DATE '2017-10-23', TRUE,   79000.00, '10000000009', 5, 4),
    (10, 'henry',  'Henry Clark',  'henry.clark@example.com',  DATE '2019-06-12', TRUE,   64000.00, '10000000010', 5, 9),
    (11, 'ivy',    'Ivy Turner',   'ivy.turner@example.com',   DATE '2024-01-08', TRUE,   52000.00, '10000000011', 5, 9),
    (12, 'jack',   'Jack Moore',   'jack.moore@example.com',   DATE '2015-08-19', TRUE,   88000.00, '10000000012', 6, 1),
    (13, 'kate',   'Kate Hall',    'kate.hall@example.com',    DATE '2020-03-30', TRUE,   59000.00, '10000000013', 6, 12),
    (14, 'liam',   'Liam Young',   'liam.young@example.com',   DATE '2023-09-11', FALSE,  50000.00, '10000000014', 6, 12),
    (15, 'mia',    'Mia Allen',    'mia.allen@example.com',    DATE '2016-12-05', TRUE,   76000.00, '10000000015', 7, 1),
    (16, 'noah',   'Noah King',    'noah.king@example.com',    DATE '2019-04-22', TRUE,   55000.00, '10000000016', 7, 15),
    (17, 'olivia', 'Olivia Scott', 'olivia.scott@example.com', DATE '2021-01-18', TRUE,   53000.00, '10000000017', 7, 15),
    (18, 'paul',   'Paul Adams',   'paul.adams@example.com',   DATE '2024-06-03', TRUE,   47000.00, '10000000018', 7, 15),
    (19, 'quinn',  'Quinn Baker',  'quinn.baker@example.com',  DATE '2025-02-10', TRUE,   54000.00, '10000000019', 4, 5),
    (20, 'rachel', 'Rachel Green', 'rachel.green@example.com', DATE '2019-11-25', FALSE,  51000.00, '10000000020', 2, 2);

-- Department managers: a second Department -> Personnel relation
UPDATE department SET manager_id = 1  WHERE id = 1;
UPDATE department SET manager_id = 2  WHERE id = 2;
UPDATE department SET manager_id = 4  WHERE id = 3;
UPDATE department SET manager_id = 5  WHERE id = 4;
UPDATE department SET manager_id = 9  WHERE id = 5;
UPDATE department SET manager_id = 12 WHERE id = 6;
UPDATE department SET manager_id = 15 WHERE id = 7;

-- ---- One-to-one: personnel profile (paul and quinn have none) ------------------------
INSERT INTO personnel_profile (id, personnel_id, birth_date, blood_type, city, emergency_contact) VALUES
    ( 1,  1, DATE '1975-04-12', 'A+',  'Istanbul', 'Steve Prince'),
    ( 2,  2, DATE '1985-09-30', 'O+',  'Istanbul', 'Tom Smith'),
    ( 3,  3, DATE '1993-02-14', 'B+',  'Ankara',   'Min Lee'),
    ( 4,  4, DATE '1982-11-05', 'A-',  'Izmir',    'Sara Jones'),
    ( 5,  5, DATE '1988-06-21', 'AB+', 'Istanbul', 'Mark White'),
    ( 6,  6, DATE '1991-03-08', 'O-',  'Ankara',   'Lisa Miller'),
    ( 7,  7, DATE '1995-12-19', 'A+',  'Istanbul', 'Luis Garcia'),
    ( 8,  8, DATE '1997-07-27', 'B-',  'Izmir',    'Anna Wilson'),
    ( 9,  9, DATE '1986-01-03', 'O+',  'Ankara',   'Jin Kim'),
    (10, 10, DATE '1990-10-10', 'A+',  'Bursa',    'Emma Clark'),
    (11, 11, DATE '1999-05-15', 'AB-', 'Istanbul', 'Sam Turner'),
    (12, 12, DATE '1980-08-08', 'B+',  'Istanbul', 'Rose Moore'),
    (13, 13, DATE '1992-04-04', 'O+',  'Ankara',   'Ben Hall'),
    (14, 14, DATE '1998-09-09', 'A-',  'Izmir',    'Zoe Young'),
    (15, 15, DATE '1984-02-29', 'O-',  'Istanbul', 'Leo Allen'),
    (16, 16, DATE '1994-06-06', 'A+',  'Bursa',    'Ava King'),
    (17, 17, DATE '1996-11-11', 'B+',  'Istanbul', 'Eli Scott'),
    (18, 20, DATE '1989-03-03', 'O+',  'Ankara',   'Ross Green');

-- ---- Pure many-to-many: skills -------------------------------------------------------
INSERT INTO skill (id, name, category) VALUES
    ( 1, 'Java',            'Technical'),
    ( 2, 'SQL',             'Technical'),
    ( 3, 'Kubernetes',      'Technical'),
    ( 4, 'React',           'Technical'),
    ( 5, 'Linux',           'Technical'),
    ( 6, 'Excel',           'Office'),
    ( 7, 'Recruiting',      'Human Resources'),
    ( 8, 'Accounting',      'Finance'),
    ( 9, 'Negotiation',     'Business'),
    (10, 'Public Speaking', 'Business');

INSERT INTO personnel_skill (personnel_id, skill_id) VALUES
    (1, 9), (1, 10),
    (2, 7), (2, 10), (2, 6),
    (3, 7), (3, 6),
    (4, 1), (4, 2), (4, 3), (4, 5),
    (5, 1), (5, 2), (5, 4),
    (6, 1), (6, 4),
    (7, 1), (7, 2), (7, 3),
    (8, 4),
    (9, 3), (9, 5), (9, 2),
    (10, 5), (10, 3),
    (11, 5),
    (12, 8), (12, 6), (12, 9),
    (13, 8), (13, 6),
    (14, 6),
    (15, 9), (15, 10), (15, 6),
    (16, 9),
    (17, 9), (17, 6),
    (19, 1),
    (20, 7);

-- ---- Many-to-many with extra columns: trainings ---------------------------------------
INSERT INTO training (id, name, category, validity_months, mandatory) VALUES
    (1, 'Occupational Health and Safety', 'Safety',     12,   TRUE),
    (2, 'Fire Safety',                    'Safety',     12,   TRUE),
    (3, 'Data Protection',                'Compliance', 24,   TRUE),
    (4, 'Anti-Bribery',                   'Compliance', 24,   TRUE),
    (5, 'First Aid',                      'Safety',     36,   FALSE),
    (6, 'Secure Coding',                  'Technical',  12,   FALSE),
    (7, 'Cloud Fundamentals',             'Technical',  NULL, FALSE),
    (8, 'Leadership Fundamentals',        'Management', NULL, FALSE);

-- Everyone has Occupational Health and Safety; offsets are days until renewal (negative = overdue)
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status) VALUES
    ( 1, 1, DATEADD('DAY', 120 - 365, CURRENT_DATE), DATEADD('DAY', 120, CURRENT_DATE),  92, 'PASSED'),
    ( 2, 1, DATEADD('DAY',  10 - 365, CURRENT_DATE), DATEADD('DAY',  10, CURRENT_DATE),  88, 'PASSED'),
    ( 3, 1, DATEADD('DAY', -15 - 365, CURRENT_DATE), DATEADD('DAY', -15, CURRENT_DATE),  75, 'PASSED'),
    ( 4, 1, DATEADD('DAY',  25 - 365, CURRENT_DATE), DATEADD('DAY',  25, CURRENT_DATE),  95, 'PASSED'),
    ( 5, 1, DATEADD('DAY',   5 - 365, CURRENT_DATE), DATEADD('DAY',   5, CURRENT_DATE),  90, 'PASSED'),
    ( 6, 1, DATEADD('DAY', 200 - 365, CURRENT_DATE), DATEADD('DAY', 200, CURRENT_DATE),  81, 'PASSED'),
    ( 7, 1, DATEADD('DAY',  40 - 365, CURRENT_DATE), DATEADD('DAY',  40, CURRENT_DATE),  77, 'PASSED'),
    ( 8, 1, DATEADD('DAY',  -3 - 365, CURRENT_DATE), DATEADD('DAY',  -3, CURRENT_DATE),  70, 'PASSED'),
    ( 9, 1, DATEADD('DAY',  18 - 365, CURRENT_DATE), DATEADD('DAY',  18, CURRENT_DATE),  98, 'PASSED'),
    (10, 1, DATEADD('DAY', 300 - 365, CURRENT_DATE), DATEADD('DAY', 300, CURRENT_DATE),  85, 'PASSED'),
    (11, 1, DATEADD('DAY',  60 - 365, CURRENT_DATE), DATEADD('DAY',  60, CURRENT_DATE),  79, 'PASSED'),
    (12, 1, DATEADD('DAY',  29 - 365, CURRENT_DATE), DATEADD('DAY',  29, CURRENT_DATE),  91, 'PASSED'),
    (13, 1, DATEADD('DAY',  90 - 365, CURRENT_DATE), DATEADD('DAY',  90, CURRENT_DATE),  84, 'PASSED'),
    (14, 1, DATEADD('DAY', -30 - 365, CURRENT_DATE), DATEADD('DAY', -30, CURRENT_DATE),  72, 'PASSED'),
    (15, 1, DATEADD('DAY',  12 - 365, CURRENT_DATE), DATEADD('DAY',  12, CURRENT_DATE),  89, 'PASSED'),
    (16, 1, DATEADD('DAY', 150 - 365, CURRENT_DATE), DATEADD('DAY', 150, CURRENT_DATE),  80, 'PASSED'),
    (17, 1, DATEADD('DAY',  45 - 365, CURRENT_DATE), DATEADD('DAY',  45, CURRENT_DATE),  86, 'PASSED'),
    (18, 1, DATEADD('DAY', 330 - 365, CURRENT_DATE), DATEADD('DAY', 330, CURRENT_DATE),  74, 'PASSED'),
    (19, 1, DATEADD('DAY', 350 - 365, CURRENT_DATE), DATEADD('DAY', 350, CURRENT_DATE),  93, 'PASSED'),
    (20, 1, DATEADD('DAY',-100 - 365, CURRENT_DATE), DATEADD('DAY',-100, CURRENT_DATE),  78, 'PASSED');

-- Data Protection (24 months)
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status) VALUES
    ( 1, 3, DATEADD('DAY', 400 - 730, CURRENT_DATE), DATEADD('DAY', 400, CURRENT_DATE), 90, 'PASSED'),
    ( 2, 3, DATEADD('DAY', 200 - 730, CURRENT_DATE), DATEADD('DAY', 200, CURRENT_DATE), 94, 'PASSED'),
    ( 4, 3, DATEADD('DAY',  15 - 730, CURRENT_DATE), DATEADD('DAY',  15, CURRENT_DATE), 87, 'PASSED'),
    ( 5, 3, DATEADD('DAY', 380 - 730, CURRENT_DATE), DATEADD('DAY', 380, CURRENT_DATE), 91, 'PASSED'),
    ( 6, 3, DATEADD('DAY', 500 - 730, CURRENT_DATE), DATEADD('DAY', 500, CURRENT_DATE), 83, 'PASSED'),
    (12, 3, DATEADD('DAY',  22 - 730, CURRENT_DATE), DATEADD('DAY',  22, CURRENT_DATE), 96, 'PASSED'),
    (13, 3, DATEADD('DAY', 700 - 730, CURRENT_DATE), DATEADD('DAY', 700, CURRENT_DATE), 88, 'PASSED'),
    (15, 3, DATEADD('DAY', 250 - 730, CURRENT_DATE), DATEADD('DAY', 250, CURRENT_DATE), 85, 'PASSED'),
    (20, 3, DATEADD('DAY',-200 - 730, CURRENT_DATE), DATEADD('DAY',-200, CURRENT_DATE), 76, 'PASSED');

-- Fire Safety (12 months)
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status) VALUES
    ( 3, 2, DATEADD('DAY',   8 - 365, CURRENT_DATE), DATEADD('DAY',   8, CURRENT_DATE), 82, 'PASSED'),
    ( 9, 2, DATEADD('DAY',  27 - 365, CURRENT_DATE), DATEADD('DAY',  27, CURRENT_DATE), 90, 'PASSED'),
    (10, 2, DATEADD('DAY', 100 - 365, CURRENT_DATE), DATEADD('DAY', 100, CURRENT_DATE), 79, 'PASSED'),
    (16, 2, DATEADD('DAY',  -5 - 365, CURRENT_DATE), DATEADD('DAY',  -5, CURRENT_DATE), 73, 'PASSED'),
    (17, 2, DATEADD('DAY',   3 - 365, CURRENT_DATE), DATEADD('DAY',   3, CURRENT_DATE), 88, 'PASSED');

-- First Aid (36 months)
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status) VALUES
    ( 2, 5, DATEADD('DAY', 600 - 1095, CURRENT_DATE), DATEADD('DAY', 600, CURRENT_DATE), 97, 'PASSED'),
    ( 9, 5, DATEADD('DAY',  20 - 1095, CURRENT_DATE), DATEADD('DAY',  20, CURRENT_DATE), 92, 'PASSED'),
    (11, 5, DATEADD('DAY', 900 - 1095, CURRENT_DATE), DATEADD('DAY', 900, CURRENT_DATE), 84, 'PASSED');

-- Secure Coding (12 months); frank failed, so no renewal date
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status) VALUES
    ( 5, 6, DATEADD('DAY', 100 - 365, CURRENT_DATE), DATEADD('DAY', 100, CURRENT_DATE), 95, 'PASSED'),
    ( 6, 6, DATEADD('DAY',  35 - 365, CURRENT_DATE), DATEADD('DAY',  35, CURRENT_DATE), 88, 'PASSED'),
    ( 7, 6, DATEADD('DAY',   2 - 365, CURRENT_DATE), DATEADD('DAY',   2, CURRENT_DATE), 81, 'PASSED'),
    ( 8, 6, DATEADD('DAY', -20, CURRENT_DATE),       NULL,                              50, 'FAILED'),
    (19, 6, DATEADD('DAY', 340 - 365, CURRENT_DATE), DATEADD('DAY', 340, CURRENT_DATE), 89, 'PASSED');

-- Cloud Fundamentals and Leadership Fundamentals never expire
INSERT INTO training_record (personnel_id, training_id, completed_on, next_renewal_date, score, status) VALUES
    ( 4, 7, DATE '2024-03-12', NULL, 91, 'PASSED'),
    ( 9, 7, DATE '2024-05-20', NULL, 94, 'PASSED'),
    (10, 7, DATE '2025-01-15', NULL, 86, 'PASSED'),
    ( 1, 8, DATE '2019-10-01', NULL, 99, 'PASSED'),
    ( 4, 8, DATE '2021-04-14', NULL, 87, 'PASSED'),
    ( 5, 8, DATE '2023-06-09', NULL, 90, 'PASSED'),
    (12, 8, DATE '2020-11-23', NULL, 85, 'PASSED'),
    (15, 8, DATE '2022-02-17', NULL, 88, 'PASSED');

-- ---- Many-to-many with extra columns across modules: projects -----------------------
INSERT INTO project (id, code, name, status, budget, start_date, end_date, department_id) VALUES
    (1, 'PRJ-001', 'Payroll Modernization',  'ACTIVE',    250000.00, DATE '2025-01-15', DATE '2026-12-31', 4),
    (2, 'PRJ-002', 'Data Center Migration',  'ACTIVE',    400000.00, DATE '2025-03-01', DATE '2027-06-30', 5),
    (3, 'PRJ-003', 'Recruitment Portal',     'COMPLETED',  90000.00, DATE '2024-02-01', DATE '2025-05-31', 2),
    (4, 'PRJ-004', 'Annual Budget Planning', 'ACTIVE',     30000.00, DATE '2026-09-01', DATE '2026-12-15', 6),
    (5, 'PRJ-005', 'CRM Rollout',            'PLANNED',   180000.00, DATE '2027-01-10', NULL,              7),
    (6, 'PRJ-006', 'Security Audit',         'ON_HOLD',    60000.00, DATE '2025-10-01', NULL,              3);

INSERT INTO project_assignment (project_id, personnel_id, role, allocation_percent, assigned_on) VALUES
    (1,  5, 'Lead',      50,  DATE '2025-01-15'),
    (1,  6, 'Developer', 100, DATE '2025-01-20'),
    (1,  7, 'Developer', 80,  DATE '2025-02-01'),
    (1, 13, 'Analyst',   30,  DATE '2025-02-10'),
    (1, 19, 'Developer', 100, DATE '2025-03-01'),
    (2,  9, 'Lead',      60,  DATE '2025-03-01'),
    (2, 10, 'Engineer',  100, DATE '2025-03-05'),
    (2, 11, 'Engineer',  100, DATE '2025-03-05'),
    (2,  4, 'Sponsor',   10,  DATE '2025-03-01'),
    (3,  2, 'Sponsor',   10,  DATE '2024-02-01'),
    (3,  3, 'Analyst',   50,  DATE '2024-02-05'),
    (3,  8, 'Developer', 100, DATE '2024-02-05'),
    (4, 12, 'Lead',      40,  DATE '2026-09-01'),
    (4, 13, 'Analyst',   50,  DATE '2026-09-01'),
    (4, 14, 'Analyst',   100, DATE '2026-09-01'),
    (5, 15, 'Sponsor',   20,  DATE '2026-11-01'),
    (5, 16, 'Analyst',   50,  DATE '2026-11-01'),
    (5,  5, 'Architect', 20,  DATE '2026-11-01'),
    (6,  4, 'Lead',      30,  DATE '2025-10-01'),
    (6, 10, 'Engineer',  40,  DATE '2025-10-01');

-- ---- Let generated ids continue after the demo rows -----------------------------------
ALTER TABLE department         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE personnel          ALTER COLUMN id RESTART WITH 100;
ALTER TABLE personnel_profile  ALTER COLUMN id RESTART WITH 100;
ALTER TABLE skill              ALTER COLUMN id RESTART WITH 100;
ALTER TABLE training           ALTER COLUMN id RESTART WITH 100;
ALTER TABLE training_record    ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE project            ALTER COLUMN id RESTART WITH 100;
ALTER TABLE project_assignment ALTER COLUMN id RESTART WITH 1000;
