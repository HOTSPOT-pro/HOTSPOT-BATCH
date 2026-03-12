package hotspot.batch.common.util.redis;

/**
 * Redis에서 조회된 값을 자바 타입으로 변환하는 유틸리티
 */
public final class RedisValueParser {

    private RedisValueParser() {}

    /**
     * 문자열 값을 Long 타입으로 안전하게 변환하며 실패 시 null을 반환함
     */
    public static Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
