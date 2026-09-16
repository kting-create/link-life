package com.linklife.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_profile")
public class UserProfile {
    @TableId
    private Long userId;
    private String tastePrefs;
    private String aiMemory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
