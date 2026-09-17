package com.linklife.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("order_item")
public class OrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sheetId;
    private String dishName;
    private String note;
    private Long claimantId;
    private String itemStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
