package hotspot.batch.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * JSON 데이터와 Java 객체 간의 변환을 담당하는 유틸리티
 * PostgreSQL의 JSONB 컬럼 처리를 위해 사용됨
 */
@Component
public class JsonConverter {

    private final ObjectMapper objectMapper;

    public JsonConverter() {
        this.objectMapper = new ObjectMapper();
        // Java 8 날짜/시간 API(LocalDate, LocalDateTime 등) 지원을 위해 모듈 등록
        this.objectMapper.registerModule(new JavaTimeModule());
        // DTO에 없는 필드가 JSON에 있어도 에러를 내지 않도록 설정
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * JSON 문자열을 Java 객체로 변환
     */
    public <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank() || clazz == null) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            // JSON 파싱 실패 시 RuntimeException으로 전환하여 트랜잭션 롤백 유도
            throw new RuntimeException("Failed to parse JSON string to " + clazz.getSimpleName(), e);
        }
    }

    /**
     * Java 객체를 JSON 문자열로 변환
     */
    public String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            // JSON 직렬화 실패 시 RuntimeException으로 전환하여 트랜잭션 롤백 유도
            throw new RuntimeException("Failed to serialize object to JSON string", e);
        }
    }
}
