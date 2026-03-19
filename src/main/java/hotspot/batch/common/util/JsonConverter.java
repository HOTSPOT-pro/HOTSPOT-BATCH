package hotspot.batch.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * JSON 데이터와 Java 객체 간의 변환을 담당하는 유틸리티
 * ObjectWriter/Reader를 재사용하여 성능 최적화
 */
@Component
public class JsonConverter {

    private final ObjectMapper objectMapper;
    private final ObjectWriter objectWriter;

    public JsonConverter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Writer를 미리 생성하여 writeValueAsString 호출 시의 오버헤드 감소
        this.objectWriter = this.objectMapper.writer();
    }

    /**
     * JSON 문자열을 Java 객체로 변환
     */
    public <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank() || clazz == null) return null;
        try {
            // Reader를 매번 생성하지 않고 cache하여 사용 가능하나, clazz가 다양하므로 기본 readValue 사용
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON string to " + clazz.getSimpleName(), e);
        }
    }

    /**
     * Java 객체를 JSON 문자열로 변환
     */
    public String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectWriter.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON string", e);
        }
    }
}
