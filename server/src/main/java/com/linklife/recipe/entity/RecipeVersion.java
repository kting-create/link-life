package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recipe_version")
public class RecipeVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Integer version;
    private String source;
    private String content;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdAt;
}
