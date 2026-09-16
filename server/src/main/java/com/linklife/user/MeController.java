package com.linklife.user;

import com.linklife.auth.AuthService;
import com.linklife.auth.dto.UserVO;
import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.user.dto.UpdateMeRequest;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserMapper userMapper;
    private final AuthService authService;

    @GetMapping
    public Result<UserVO> me() {
        User user = userMapper.selectById(UserContext.requireUserId());
        return Result.ok(authService.toVO(user));
    }

    @PutMapping
    public Result<UserVO> updateMe(@RequestBody UpdateMeRequest request) {
        User user = userMapper.selectById(UserContext.requireUserId());
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        userMapper.updateById(user);
        return Result.ok(authService.toVO(user));
    }
}
