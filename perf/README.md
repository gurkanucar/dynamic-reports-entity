# Performance test on PostgreSQL

Runs every example of `http/reports.http` against PostgreSQL with ~7.8M rows (1M personnel), shows
the query plans of slow reports and load-tests with parallel users.

```bash
# 1. PostgreSQL 17 on localhost:5433 (data kept in a docker volume)
docker compose -f perf/compose.yaml up -d

# 2. Start the app once on PostgreSQL: Hibernate creates the tables and indexes
./mvnw -DskipTests package
java -jar target/dynamic-reports-entity-0.0.1-SNAPSHOT.jar --spring.profiles.active=postgres --server.port=8082

# 3. Load demo + bulk data (~45 s for 1M personnel; any scale works, e.g. 5000000)
perf/load-postgres.sh 1000000

# 4. Measure
python perf/bench.py --base http://localhost:8082 --runs 5 --explain 300   # each report; plans -> target/perf-plans/
python perf/bench.py --base http://localhost:8082 --no-total               # pages without "total" (the API default)
python perf/bench.py --base http://localhost:8082 --concurrency 16 --duration 60 --only 2,5,6,8,9
```

`--explain` takes the SQL from `/api/reports/preview`, inlines the parameters and runs
`EXPLAIN (ANALYZE, BUFFERS)` in the container. Slow reports are also logged by the app
(`reporting.slow-query-threshold`) together with the missing-index advice.

## Results (1M personnel, 16-core dev machine, PostgreSQL 17 in Docker)

| Kind of report | Examples | First page, single user |
|---|---|---|
| Detail with filters / joins up to 4 levels | 5, 6, 8, 9, 27, 39 | 70–300 ms (database 2–240 ms) |
| Per-person nested lists (LIST, objects) | 30, 32, 41 | 150–400 ms |
| Grouped over a filtered or small set | 2, 14–17, 46 | 180–390 ms |
| Field vs. group aggregate | 45, 47 | 0.4–0.9 s |
| Grouped over a whole 1M-row table | 3, 12, 13, 28 | 1.2–2.3 s |
| perRoot filter / sort on a child aggregate | 42, 43 | 2 s (was 2.8–4 s) |
| Lists through two one-to-many hops, 200 roots | 33, 34 | 7–7.5 s |
| CSV export | 21 | 2.1 s |

Load, 16 parallel users, the 23 interactive reports: 14.6 req/s, p50 0.8 s, p95 2.9 s
(without `total`: 21.9 req/s, p50 0.55 s, p95 1.9 s).

## What costs what

* **`total`** runs a second COUNT(*) over all matching rows. A page of #39 takes 5 ms in the database,
  counting its 1.25M rows ~400 ms. It was about a third of the load, so it is off by default now
  (`includeTotal=true` turns it on).
* **Whole-table aggregates** (grouped reports without a selective filter, field vs. group aggregate) read
  every row of the table no matter how small the page is; indexes cannot help, only less data
  (filters), pre-computed summaries (materialized views) or a replica do.
* **perRoot `having` / `sort` on a child aggregate** is needed for every root row, not only for the page.
  On PostgreSQL it is computed in one GROUP BY pass and joined (shared by having and sort): #42's filter
  part went from 1.1 s to 0.13 s, the rest is the page's columns. H2 keeps correlated subqueries (it cannot
  index a joined derived table), see `SqlDialect`.
* **Lists through two one-to-many relations** for 200 departments touch ~400k people and ~1M child rows.
* **HTTP Basic with the demo users**: Spring Security upgrades `{noop}` passwords to bcrypt after the first
  login, so every request spends ~55 ms hashing. Production uses tokens or sessions.

## Datasets (saved reports for flows)

Heavy reports that flows read again and again can be saved as a SNAPSHOT dataset (`/api/datasets`,
examples 48–57 in `http/reports.http`). Department 360 (#33) on 1M personnel:

| | Time |
|---|---|
| Live, first page of 200 departments (`/api/reports/run`) | 7.5 s |
| Snapshot refresh, all 506 departments (background, every `refreshEveryHours`) | 12 s |
| Snapshot read, a page (`/api/datasets/department_360/rows`) | 70 ms (database < 5 ms) |

Several app instances on one database: the schedulers of all instances look for due datasets every
minute; an atomic claim (lease in `report_dataset`, renewed every 30 s) lets exactly one of them refresh.
Tried with two instances on PostgreSQL: simultaneous refresh calls on both ran one build and both answered
with its result; killing the refreshing instance mid-build, the other one took over once the lease ran
out and completed the snapshot.
