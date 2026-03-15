package hotspot.batch.common.exception;

/**
 * 프롬프트 템플릿 파일을 읽어오는 과정에서 문제가 발생했을 때 던지는 예외
 */
public class LlmPromptTemplateLoadException extends LlmFeedbackException {
    public LlmPromptTemplateLoadException(String message, String path, Throwable cause) {
        super(String.format("%s (Path: %s)", message, path), cause);
    }
}
