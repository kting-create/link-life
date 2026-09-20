#!/usr/bin/env bash
# 每日备份:mysqldump → $BACKUP_DIR,保留 14 天
# crontab 示例:0 3 * * * cd /opt/link-life/deploy && ./backup.sh >> ../logs/backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")"
set -a; [ -f .env ] && source .env; set +a
: "${DB_PASSWORD:?DB_PASSWORD is required (deploy/.env)}"

BACKUP_DIR="${BACKUP_DIR:-/opt/link-life/backups}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
DB_USER="${DB_USER:-linklife}"
DB_NAME="${DB_NAME:-linklife}"
KEEP_DAYS="${KEEP_DAYS:-14}"
mkdir -p "$BACKUP_DIR"

STAMP=$(date +%Y%m%d-%H%M%S)
docker compose -f "$COMPOSE_FILE" exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql \
    mysqldump -u"$DB_USER" --single-transaction --routines --no-tablespaces "$DB_NAME" \
    | gzip > "$BACKUP_DIR/linklife-$STAMP.sql.gz"

find "$BACKUP_DIR" -name "linklife-*.sql.gz" -mtime +"$KEEP_DAYS" -delete
echo "backup done: linklife-$STAMP.sql.gz"
