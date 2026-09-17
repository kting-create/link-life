package com.linklife.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("dish")
public class Dish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long circleId;
    private Long userId;
    private String name;
    private Long recipeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
