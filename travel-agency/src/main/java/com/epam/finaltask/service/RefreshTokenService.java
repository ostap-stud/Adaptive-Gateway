package com.epam.finaltask.service;

import java.util.concurrent.TimeUnit;

public interface RefreshTokenService {
    void saveRefreshToken(String username, String refreshToken, long expiration, TimeUnit timeUnit);
    String getRefreshToken(String username);
    void deleteRefreshToken(String username);
}
