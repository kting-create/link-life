package com.linklife.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("order_sheet")
public class OrderSheet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long circleId;
    private Long creatorId;
    private String title;
    private String status;
    private String shareToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
