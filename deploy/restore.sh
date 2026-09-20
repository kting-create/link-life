#!/usr/bin/env bash
# 恢复演练:dump → 临时库,校验表行数后提示清理
# 用法: ./restore.sh backups/linklife-YYYYMMDD-HHMMSS.sql.gz
# 注:建临时库/恢复需要 root(linklife 用户仅有 linklife 库权限),密码取 MYSQL_ROOT_PASSWORD
set -euo pipefail

cd "$(dirname "$0")"
DUMP_FILE="${1:?用法: restore.sh <dump.sql.gz>}"
# 支持相对调用方目录传入的路径
if [ ! -f "$DUMP_FILE" ] && [ -f "$OLDPWD/$DUMP_FILE" ]; then
    DUMP_FILE="$OLDPWD/$DUMP_FILE"
fi
[ -f "$DUMP_FILE" ] || { echo "文件不存在: $DUMP_FILE"; exit 1; }

set -a; [ -f .env ] && source .env; set +a
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required (deploy/.env)}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
RESTORE_DB="linklife_restore_$(date +%s)"

echo "== 恢复到临时库 $RESTORE_DB =="
docker compose -f "$COMPOSE_FILE" exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    mysql -uroot -e "CREATE DATABASE \`$RESTORE_DB\`;"
gzip -dc "$DUMP_FILE" | docker compose -f "$COMPOSE_FILE" exec -T \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql mysql -uroot "$RESTORE_DB"

echo "== 行数抽查 =="
docker compose -f "$COMPOSE_FILE" exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    mysql -uroot -e \
    "SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema='$RESTORE_DB';"

echo "== 演练完成:确认以上表与行数符合预期后清理 =="
echo "docker compose -f $COMPOSE_FILE exec -T mysql mysql -uroot -p -e 'DROP DATABASE \`$RESTORE_DB\`;'"
echo "EXECUTE_CLEANUP: $RESTORE_DB"
