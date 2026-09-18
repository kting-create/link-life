CREATE TABLE recipe_photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    step_no INT NOT NULL COMMENT '菜谱步骤号，从 1 起',
    uploader_id BIGINT NOT NULL,
    file_path VARCHAR(255) NOT NULL COMMENT '相对路径 images/recipes/{recipeId}/{uuid}.{ext}',
    size_bytes BIGINT NOT NULL,
    analysis JSON NULL COMMENT '视觉分析结果 {advice, changes[]}',
    analyzed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_photo_recipe (recipe_id, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
