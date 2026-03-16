package hotspot.batch.common.exception;

/**
 * LLM 응답 JSON 데이터 파싱 실패 시 발생하는 예외
 */
public class LlmJsonParsingException extends LlmFeedbackException {
    public LlmJsonParsingException(String message, String rawJson, Throwable cause) {
        super(String.format("%s (Raw JSON: %s)", message, rawJson), cause);
    }
}
