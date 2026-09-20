package com.linklife.auth;

import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.jwt.JwtService;
import com.linklife.auth.jwt.TokenInfo;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.UserService;
import com.linklife.user.entity.User;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final WeChatClient weChatClient;
    private final UserService userService;
    private final JwtService jwtService;

    public AuthTokens wxLogin(String jsCode) {
        WxSession session;
        try {
            session = weChatClient.code2Session(jsCode);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("wx login transport error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
        User user = userService.getUserByOpenid(session.openid());
        if (user == null) {
            try {
                user = userService.createUser(session.openid(), session.unionid());
            } catch (DuplicateKeyException e) {
                // 并发首登:唯一键兜底后重查(参照 RecipeService.createRecipeWithV1 范式)
                user = userService.getUserByOpenid(session.openid());
            }
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
        return buildTokens(user);
    }

    public AuthTokens refresh(String refreshToken) {
        TokenInfo info;
        try {
            info = jwtService.parse(refreshToken);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (!"refresh".equals(info.type())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        User user = userService.getUserById(info.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (verOf(user) != info.ver()) {
            throw new BusinessException(ErrorCode.TOKEN_REVOKED);
        }
        return buildTokens(user);
    }

    public void logout(long userId) {
        userService.bumpTokenVersion(userId);
    }

    private AuthTokens buildTokens(User user) {
        long ver = verOf(user);
        return new AuthTokens(
                jwtService.generateAccessToken(user.getId(), ver),
                jwtService.generateRefreshToken(user.getId(), ver),
                userService.toVO(user));
    }

    private long verOf(User user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }
}
