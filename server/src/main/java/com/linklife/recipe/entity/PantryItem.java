package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("pantry_item")
public class PantryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String name;
    private String note;
    private LocalDateTime createdAt;
}
