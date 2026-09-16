package com.linklife.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("circle")
public class Circle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long ownerId;
    private String inviteCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
