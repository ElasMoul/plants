package com.plantpal.shared.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

  private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

  @Override
  public CacheErrorHandler errorHandler() {
    return new SimpleCacheErrorHandler() {
      @Override
      public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        log.warn("Cache GET error — cache={}, key={}: {}", cache.getName(), key, e.getMessage());
      }

      @Override
      public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        log.warn("Cache PUT error — cache={}, key={}: {}", cache.getName(), key, e.getMessage());
      }

      @Override
      public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        log.warn("Cache EVICT error — cache={}, key={}: {}", cache.getName(), key, e.getMessage());
      }

      @Override
      public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.warn("Cache CLEAR error — cache={}: {}", cache.getName(), e.getMessage());
      }
    };
  }

  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    // Dedicated ObjectMapper for Redis — separate from the HTTP ObjectMapper.
    // JavaTimeModule: handles LocalDate, Instant, etc.
    // WRITE_DATES_AS_TIMESTAMPS=false: ISO-8601 strings in Redis (readable + portable).
    // Default typing: embeds @class in JSON so Spring can deserialize Page<T> and other
    // generic types back to the correct concrete type on cache read.
    ObjectMapper redisMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(redisMapper)));

    // PlantNet flora / language lists are stable — cache for 24h so metadata calls never burn
    // identify quota. Quota endpoint is refreshed every 5 min (short enough to stay useful,
    // long enough not to burn requests just checking the counter). All other caches use the
    // 10-minute default above.
    RedisCacheConfiguration longLivedConfig = config.entryTtl(Duration.ofHours(24));
    RedisCacheConfiguration quotaConfig = config.entryTtl(Duration.ofMinutes(5));
    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(config)
        .withCacheConfiguration("plantnet-projects", longLivedConfig)
        .withCacheConfiguration("plantnet-languages", longLivedConfig)
        .withCacheConfiguration("plantnet-quota", quotaConfig)
        .build();
  }

  // Raw byte[] storage for photo bytes (separate from the JSON-serialising cache above).
  // Not @Primary — must not replace the default RedisTemplate<Object,Object> used for caching.
  @Bean
  public RedisTemplate<String, byte[]> byteRedisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, byte[]> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(RedisSerializer.byteArray());
    return template;
  }
}
