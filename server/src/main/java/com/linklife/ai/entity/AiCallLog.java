package com.linklife.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_call_log")
public class AiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String scene;
    private String provider;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Boolean ok;
    private String errorMsg;
    private LocalDateTime createdAt;
}
