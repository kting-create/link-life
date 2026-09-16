package com.linklife.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.user.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM `user` WHERE openid = #{openid}")
    User selectByOpenid(@Param("openid") String openid);
}
