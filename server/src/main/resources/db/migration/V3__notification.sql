CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收人',
    type VARCHAR(32) NOT NULL COMMENT 'SHEET_SHARED / ITEM_CLAIMED / ITEM_DONE / SHEET_COMPLETED',
    title VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    sheet_id BIGINT NULL COMMENT '跳转目标（清单）',
    circle_id BIGINT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_read (user_id, is_read),
    KEY idx_user_id (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
