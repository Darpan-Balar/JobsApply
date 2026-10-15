#!/usr/bin/env bash
# Checks a backup: file checksums, then a full restore into a scratch database whose row counts must equal the
# manifest. Exit status 0 means the backup can be restored. Nothing existing is touched.
#
# Usage: scripts/verify-backup.sh <backup-dir>
source "$(dirname "$0")/backup-lib.sh"

DIR="${1:?usage: verify-backup.sh <backup-dir>}"
[ -f "$DIR/manifest.properties" ] || die "$DIR does not look like a backup (no manifest.properties)"
(cd "$DIR" && sha256_check SHA256SUMS) || die "checksum mismatch: the backup is damaged"
tar -tzf "$DIR/documents.tar.gz" >/dev/null || die "documents.tar.gz is unreadable"

SCRATCH="jobs_verify_check_$$"
trap 'drop_db "$SCRATCH" || true' EXIT
restore_db "$DIR/database.dump" "$SCRATCH"
expected="$(grep '^rows\.' "$DIR/manifest.properties")"
actual="$(table_counts "$SCRATCH")"
if [ "$expected" != "$actual" ]; then
  diff <(echo "$expected") <(echo "$actual") >&2 || true
  die "restored row counts differ from the manifest"
fi
echo "OK: $DIR restores completely ($(echo "$actual" | wc -l | tr -d ' ') tables, counts match)"
