# Shared helpers for backup.sh, verify-backup.sh and restore.sh (sourced, not executed).
# Settings (environment): JOBS_DB_CONTAINER (default: the compose "postgres" service), JOBS_DB_NAME (jobs),
# JOBS_DB_USER (jobs), JOBS_STORAGE_DIR (data). Commands run inside the database container over its local socket.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_NAME="${JOBS_DB_NAME:-jobs}"
DB_USER="${JOBS_DB_USER:-jobs}"
STORAGE_DIR="${JOBS_STORAGE_DIR:-$ROOT/data}"

container() {
  if [ -n "${JOBS_DB_CONTAINER:-}" ]; then
    echo "$JOBS_DB_CONTAINER"
  else
    (cd "$ROOT" && docker compose ps -q postgres)
  fi
}

die() {
  echo "error: $*" >&2
  exit 1
}

# psql_q <database> <sql> : one value per line, no headers
psql_q() {
  docker exec "$(container)" psql -U "$DB_USER" -d "$1" -AtX -v ON_ERROR_STOP=1 -c "$2"
}

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi
}

sha256_check() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum -c --quiet "$@"; else shasum -a 256 -c --quiet "$@"; fi
}

valid_db_name() {
  [[ "$1" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] || die "invalid database name '$1' (lower-case letters, digits, '_')"
}

db_exists() {
  [ "$(psql_q postgres "select count(*) from pg_database where datname = '$1'")" = "1" ]
}

# restore_db <dump-file> <new-database> : creates the database and restores the dump into it
restore_db() {
  valid_db_name "$2"
  db_exists "$2" && die "database '$2' already exists; choose another name (restores never overwrite)"
  psql_q postgres "create database \"$2\"" >/dev/null
  docker exec -i "$(container)" pg_restore -U "$DB_USER" -d "$2" --no-owner --exit-on-error < "$1"
}

# table_counts <database> : "rows.<table>=<count>" for every table, sorted
table_counts() {
  local table
  for table in $(psql_q "$1" "select tablename from pg_tables where schemaname = 'public' order by tablename"); do
    echo "rows.$table=$(psql_q "$1" "select count(*) from \"$table\"")"
  done
}

drop_db() {
  psql_q postgres "drop database if exists \"$1\"" >/dev/null
}
