package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recipe_photo")
public class RecipePhoto {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Integer stepNo;
    private Long uploaderId;
    private String filePath;
    private Long sizeBytes;
    private String analysis;
    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
}
