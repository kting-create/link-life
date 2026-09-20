-- refresh-token 吊销基础:user.token_version
ALTER TABLE `user` ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;

-- 索引修正:unionid 业务上应唯一(MySQL 唯一索引允许多个 NULL)
ALTER TABLE `user` DROP INDEX uk_unionid, ADD UNIQUE INDEX uk_unionid (unionid);

-- binding_code 按 code 取最新未用码,语义保留历史码,仅消除 uk 命名误导
ALTER TABLE binding_code DROP INDEX uk_code, ADD INDEX idx_code (code);
