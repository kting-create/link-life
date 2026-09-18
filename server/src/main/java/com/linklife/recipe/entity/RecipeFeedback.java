package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recipe_feedback")
public class RecipeFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Long userId;
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;
}
