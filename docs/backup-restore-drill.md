# 备份与恢复演练手册

## 目标

验证 `deploy/backup.sh`(每日备份)与 `deploy/restore.sh`(恢复演练)可用,确保 dump 文件真实可恢复——**备份没验证过恢复,等于没有备份**。

## 前置条件

- `deploy/` 下 `.env` 已配置 `DB_PASSWORD`(备份用)与 `MYSQL_ROOT_PASSWORD`(恢复演练用,需 root 建临时库)。
- compose 栈在跑:`cd deploy && docker compose up -d`,等 `mysql` healthy(`docker compose ps`)。

## 一、本地演练步骤

### 1. 造点数据(可选但推荐)

插入一条可识别的标记行,便于恢复后确认数据真的回来了:

```bash
cd deploy
docker compose exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql mysql -ulinklife linklife \
  -e "INSERT INTO user (openid, nickname) VALUES ('drill-marker-$(date +%s)', 'backup-drill');"
```

### 2. 备份

```bash
./backup.sh
# 输出: backup done: linklife-YYYYMMDD-HHMMSS.sql.gz
```

默认写入 `/opt/link-life/backups/`,本地演练可用临时目录避免污染:

```bash
BACKUP_DIR=/tmp/p5-backup ./backup.sh
```

脚本行为:mysqldump(`--single-transaction --routines --no-tablespaces`)→ gzip → 按保留天数(`KEEP_DAYS`,默认 14)清理旧 dump。密码经 `MYSQL_PWD` 环境变量传入容器,不出现在进程列表。

### 3. 恢复到临时库

```bash
./restore.sh backups/linklife-YYYYMMDD-HHMMSS.sql.gz
```

脚本行为:创建临时库 `linklife_restore_<时间戳>` → 解压灌入 → 输出该库所有表的行数清单。**它不会碰 `linklife` 主库**,演练完需手动清理(见第 5 步)。

### 4. 核对表行数

脚本末尾的行数表来自 `information_schema.tables.table_rows`,**是 InnoDB 估算值,刚导入后可能严重失真(甚至显示 0),只能当参考**。必须用精确 COUNT(*) 对比源库与临时库:

```bash
docker compose exec -T mysql mysql -uroot -p -e \
  "SELECT (SELECT COUNT(*) FROM linklife.user)            AS src_user,
          (SELECT COUNT(*) FROM \`linklife_restore_XXX\`.user) AS rst_user;"
```

对核心表(`user` / `order_sheet` / `order_item` / `recipe` / `notification` 等)逐一核对,并确认第 1 步的标记行在临时库中存在。

### 5. 清理临时库

```bash
docker compose exec -T mysql mysql -uroot -p \
  -e "DROP DATABASE \`linklife_restore_XXX\`;"
```

确认清理干净:

```bash
docker compose exec -T mysql mysql -uroot -p -e "SHOW DATABASES LIKE 'linklife_restore%';"
```

演练插入的标记行可顺手从源库删除。

## 二、🧑 服务器侧确认(统一验证阶段)

在服务器上重复一遍上述流程并确认:

- [ ] crontab 已配置(示例:`0 3 * * * cd /opt/link-life/deploy && ./backup.sh >> ../logs/backup.log 2>&1`),次日凌晨 dump 文件已生成
- [ ] `BACKUP_DIR` 指向服务器实际备份目录,磁盘余量足够
- [ ] 取最新 dump 执行 `./restore.sh <dump>`,核心表行数与线上库一致(精确 COUNT(*))
- [ ] 临时库演练后已 DROP 清理
- [ ] `KEEP_DAYS` 保留策略生效(手工改一个旧 dump 的 mtime 验证删除)

## 三、演练结果

> 以下由执行演练的人填写。

| 项 | 值 |
|---|---|
| 演练日期 | 待填 |
| 执行人 | 待填 |
| dump 文件 | 待填 |
| 核心表行数(源库 vs 临时库) | 待填 |
| 标记行确认 | 待填 |
| 临时库清理 | 待填 |
| 结论 | 待填 |

### 已知注意事项(来自本地演练)

1. `mysqldump` 需加 `--no-tablespaces`,否则 `linklife` 用户因缺 PROCESS 权限报错(备份脚本已内置)。
2. 恢复演练必须用 root 建临时库,`linklife` 用户仅有 `linklife` 库权限(`restore.sh` 已内置,取 `.env` 的 `MYSQL_ROOT_PASSWORD`)。
3. `information_schema.tables.table_rows` 是估算值,核对行数务必用精确 `COUNT(*)`。
