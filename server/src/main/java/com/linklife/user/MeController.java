package com.linklife.user;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.user.dto.UpdateMeRequest;
import com.linklife.user.dto.UserVO;
import com.linklife.user.entity.User;
import com.linklife.user.service.TasteProfileService;
import java.util.Map;
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

    private final UserService userService;
    private final TasteProfileService tasteProfileService;

    @GetMapping
    public Result<UserVO> me() {
        User user = userService.getUserById(UserContext.requireUserId());
        return Result.ok(userService.toVO(user));
    }

    @PutMapping
    public Result<UserVO> updateMe(@RequestBody UpdateMeRequest request) {
        User user = userService.getUserById(UserContext.requireUserId());
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        userService.updateUser(user);
        return Result.ok(userService.toVO(user));
    }

    @GetMapping("/taste-profile")
    public Result<Map<String, Object>> tasteProfile() {
        return Result.ok(tasteProfileService.get(UserContext.requireUserId()));
    }
}
