#!/usr/bin/env bash
# Restores a backup WITHOUT overwriting anything: the database goes into a NEW database and the documents into a NEW
# directory. To switch the platform over, stop it, then point it at the restored copies (or rename them), e.g.:
#   ALTER DATABASE jobs RENAME TO jobs_before_restore;  ALTER DATABASE <new-database> RENAME TO jobs;
#   mv data data_before_restore && mv <new-documents-dir> data
# The restored data is only readable with the same JOBS_ENCRYPTION_KEY that was in use when it was backed up.
#
# Usage: scripts/restore.sh <backup-dir> <new-database> <new-documents-dir>
source "$(dirname "$0")/backup-lib.sh"

DIR="${1:?usage: restore.sh <backup-dir> <new-database> <new-documents-dir>}"
TARGET_DB="${2:?usage: restore.sh <backup-dir> <new-database> <new-documents-dir>}"
TARGET_DOCS="${3:?usage: restore.sh <backup-dir> <new-database> <new-documents-dir>}"

(cd "$DIR" && sha256_check SHA256SUMS) || die "checksum mismatch: the backup is damaged"
[ -e "$TARGET_DOCS" ] && [ -n "$(ls -A "$TARGET_DOCS" 2>/dev/null)" ] && die "$TARGET_DOCS exists and is not empty (restores never overwrite)"

restore_db "$DIR/database.dump" "$TARGET_DB"
umask 077
mkdir -p "$TARGET_DOCS"
tar -C "$TARGET_DOCS" -xzf "$DIR/documents.tar.gz"
echo "Restored into database '$TARGET_DB' and directory $TARGET_DOCS. Nothing existing was changed."
