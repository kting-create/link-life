package com.linklife.auth;

import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.dto.BindingCodeVO;
import com.linklife.auth.entity.BindingCode;
import com.linklife.auth.jwt.JwtService;
import com.linklife.auth.mapper.BindingCodeMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
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
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final AuthService authService;

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
        bc.setUsedAt(LocalDateTime.now());
        bindingCodeMapper.updateById(bc);

        User user = userMapper.selectById(bc.getUserId());
        return new AuthTokens(
                jwtService.generateAccessToken(user.getId()),
                jwtService.generateRefreshToken(user.getId()),
                authService.toVO(user));
    }
}
