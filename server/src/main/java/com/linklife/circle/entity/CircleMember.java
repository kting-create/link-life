package com.linklife.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("circle_member")
public class CircleMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long circleId;
    private Long userId;
    private String role;
    private LocalDateTime joinTime;
}
