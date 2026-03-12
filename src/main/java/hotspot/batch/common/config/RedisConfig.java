package hotspot.batch.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 접속 및 직렬화 설정을 담당하는 설정 클래스
 */
@Configuration
public class RedisConfig {

    /**
     * 문자열 기반의 Redis 조작을 위한 StringRedisTemplate 빈 등록
     * 기본적으로 StringRedisSerializer를 사용하도록 설정됨
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);

        // Key 및 Hash Key 직렬화 설정 (문자열)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 및 Hash Value 직렬화 설정 (문자열)
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
