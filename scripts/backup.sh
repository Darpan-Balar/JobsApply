#!/usr/bin/env bash
# Backs up the platform: the database (pg_dump, custom format) and the encrypted document store.
# The backup is verified as it is made: it is restored into a scratch database whose row counts go into the manifest.
# It never contains the encryption key: keep JOBS_ENCRYPTION_KEY backed up separately, or the documents and
# personal data in the backup cannot be read.
#
# Usage: scripts/backup.sh [output-root]        (default: backups/, git-ignored)
source "$(dirname "$0")/backup-lib.sh"

OUT_ROOT="${1:-$ROOT/backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$OUT_ROOT/jobs-$STAMP"
[ -n "$(container)" ] || die "the database container is not running (docker compose up -d)"

umask 077
mkdir -p "$OUT"
echo "Backing up database '$DB_NAME' and documents from $STORAGE_DIR to $OUT"
docker exec "$(container)" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --no-owner > "$OUT/database.dump"
if [ -d "$STORAGE_DIR" ]; then
  tar -C "$STORAGE_DIR" -czf "$OUT/documents.tar.gz" .
else
  tar -czf "$OUT/documents.tar.gz" -T /dev/null
fi

SCRATCH="jobs_backup_check_$$"
trap 'drop_db "$SCRATCH" || true' EXIT
restore_db "$OUT/database.dump" "$SCRATCH"
{
  echo "created_at=$STAMP"
  echo "database=$DB_NAME"
  echo "schema_version=$(psql_q "$SCRATCH" "select version from flyway_schema_history where success order by installed_rank desc limit 1")"
  echo "documents=$(tar -tzf "$OUT/documents.tar.gz" | grep -c -v '/$' || true)"
  table_counts "$SCRATCH"
} > "$OUT/manifest.properties"
(cd "$OUT" && sha256 database.dump documents.tar.gz manifest.properties > SHA256SUMS)

echo "Backup verified (restored into a scratch database): $OUT"
echo "Reminder: the backup does not contain JOBS_ENCRYPTION_KEY. Keep that key backed up separately."
