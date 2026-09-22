package com.epam.finaltask.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void saveRefreshToken(String username, String refreshToken, long expiration, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(username, refreshToken, expiration, timeUnit);
    }

    @Override
    public String getRefreshToken(String username) {
        return redisTemplate.opsForValue().get(username);
    }

    @Override
    public void deleteRefreshToken(String username) {
        redisTemplate.delete(username);
    }
}
