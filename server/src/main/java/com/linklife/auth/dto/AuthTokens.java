package com.linklife.auth.dto;

public record AuthTokens(String accessToken, String refreshToken, UserVO user) {
}
