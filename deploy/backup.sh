#!/usr/bin/env bash
# 每日备份：mysqldump 到 /opt/link-life/backups，保留 14 天
# crontab 示例：0 3 * * * /opt/link-life/deploy/backup.sh >> /opt/link-life/logs/backup.log 2>&1
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/link-life/backups}"
KEEP_DAYS=14
mkdir -p "$BACKUP_DIR"

STAMP=$(date +%Y%m%d-%H%M%S)
docker compose -f /opt/link-life/deploy/docker-compose.yml exec -T mysql \
    mysqldump -ulinklife -p"${DB_PASSWORD:-linklife-prod}" linklife \
    | gzip > "$BACKUP_DIR/linklife-$STAMP.sql.gz"

find "$BACKUP_DIR" -name "linklife-*.sql.gz" -mtime +$KEEP_DAYS -delete
echo "backup done: linklife-$STAMP.sql.gz"
