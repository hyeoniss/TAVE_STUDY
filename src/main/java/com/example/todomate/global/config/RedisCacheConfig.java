package com.example.todomate.global.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

// @Cacheable과 @CacheEvict 기반의 Spring Cache 기능을 활성화한다.
@EnableCaching
@Configuration
public class RedisCacheConfig {

    // Redis를 캐시 저장소로 사용하고 만료 시간과 직렬화 방식을 설정한다.
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                // 오래된 캐시가 계속 남지 않도록 10분 후 자동 만료한다.
                .entryTtl(Duration.ofMinutes(10))
                // null 조회 결과는 캐시에 저장하지 않는다.
                .disableCachingNullValues()
                // Redis에서 캐시 키를 읽기 쉬운 문자열로 저장한다.
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                // 페이지 응답 객체를 Redis에 JSON 형태로 저장한다.
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        RedisSerializer.json()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }
}
