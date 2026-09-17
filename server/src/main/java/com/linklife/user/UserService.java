package com.linklife.user;

import com.linklife.user.dto.UserVO;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public User getUserById(long id) {
        return userMapper.selectById(id);
    }

    public User getUserByOpenid(String openid) {
        return userMapper.selectByOpenid(openid);
    }

    public User createUser(String openid, String unionid) {
        User user = new User();
        user.setOpenid(openid);
        user.setUnionid(unionid);
        userMapper.insert(user);
        return user;
    }

    public void updateUser(User user) {
        userMapper.updateById(user);
    }

    public UserVO toVO(User user) {
        return new UserVO(user.getId(), user.getNickname(), user.getAvatar());
    }

    public Map<Long, String> getNicknames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() == null ? "用户" + u.getId() : u.getNickname(),
                        (a, b) -> a));
    }
}
