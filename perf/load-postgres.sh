#!/usr/bin/env bash
# Loads the demo data + scaled bulk data into the perf PostgreSQL (see perf/README.md).
# The tables must exist: start the app once with --spring.profiles.active=postgres first.
#   perf/load-postgres.sh            # 1,000,000 personnel
#   perf/load-postgres.sh 5000000    # 5,000,000 personnel
set -euo pipefail
cd "$(dirname "$0")/.."
SCALE="${1:-1000000}"
PSQL=(docker exec -i reports-postgres psql -U reports -d reports -v ON_ERROR_STOP=1 -q)

echo "Emptying the tables"
"${PSQL[@]}" -c "TRUNCATE department, personnel, personnel_profile, skill, personnel_skill, training,
                 training_record, project, project_assignment RESTART IDENTITY"

echo "Demo data (data.sql with H2's DATEADD translated)"
sed -E "s/DATEADD\('DAY', *([^,]+), *CURRENT_DATE\)/(CURRENT_DATE + (\1))/g" src/main/resources/data.sql \
  | "${PSQL[@]}"

echo "Bulk data, scale=$SCALE"
"${PSQL[@]}" -v scale="$SCALE" < perf/postgres-data.sql

"${PSQL[@]}" -c "SELECT relname AS table, n_live_tup AS rows, pg_size_pretty(pg_total_relation_size(relid)) AS size
                 FROM pg_stat_user_tables ORDER BY n_live_tup DESC"
