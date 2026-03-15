package hotspot.batch.common.exception;

/**
 * LLM 피드백 도메인에서 발생하는 예외들의 최상위 추상 클래스
 */
public abstract class LlmFeedbackException extends RuntimeException {
    protected LlmFeedbackException(String message) {
        super(message);
    }

    protected LlmFeedbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
