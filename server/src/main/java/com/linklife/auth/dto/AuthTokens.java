package com.linklife.auth.dto;

import com.linklife.user.dto.UserVO;

public record AuthTokens(String accessToken, String refreshToken, UserVO user) {
}
