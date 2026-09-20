package com.linklife.auth;

import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.dto.BindingCodeVO;
import com.linklife.auth.entity.BindingCode;
import com.linklife.auth.jwt.JwtService;
import com.linklife.auth.mapper.BindingCodeMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.UserService;
import com.linklife.user.entity.User;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BindingCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TTL_MINUTES = 10;

    private final BindingCodeMapper bindingCodeMapper;
    private final UserService userService;
    private final JwtService jwtService;

    @Transactional
    public BindingCodeVO create(long userId) {
        BindingCode bc = new BindingCode();
        bc.setCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
        bc.setUserId(userId);
        bc.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        bindingCodeMapper.insert(bc);
        return new BindingCodeVO(bc.getCode(), bc.getExpiresAt());
    }

    @Transactional
    public AuthTokens bind(String code) {
        BindingCode bc = bindingCodeMapper.selectByCode(code);
        if (bc == null || bc.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BINDING_CODE_INVALID);
        }
        if (bindingCodeMapper.markUsed(bc.getId()) == 0) {
            throw new BusinessException(ErrorCode.BINDING_CODE_INVALID);
        }

        User user = userService.getUserById(bc.getUserId());
        long ver = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return new AuthTokens(
                jwtService.generateAccessToken(user.getId(), ver),
                jwtService.generateRefreshToken(user.getId(), ver),
                userService.toVO(user));
    }
}
