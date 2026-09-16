package com.linklife.user;

import com.linklife.IntegrationTestBase;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserMapperTest extends IntegrationTestBase {
    @Autowired
    private UserMapper userMapper;

    @Test
    void insertAndSelectByOpenid() {
        User u = new User();
        u.setOpenid("openid-abc");
        u.setNickname("小明");
        userMapper.insert(u);

        User found = userMapper.selectByOpenid("openid-abc");
        assertNotNull(found);
        assertEquals("小明", found.getNickname());
    }
}
