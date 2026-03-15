package hotspot.batch.jobs.llm_feedback.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * LLM 피드백 Job 전용 상수 관리
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LlmBatchConstants {

    // LLM 서비스 관련 기본 설정
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final String PROMPT_VERSION = "v1.0";
    public static final double DEFAULT_TEMPERATURE = 0.7;

    // 경로 설정
    public static final String PROMPT_TEMPLATE_PATH = "prompts/llm_feedback_v1.st";

    // 인프라 설정 (Chunk 및 Skip 관련)
    public static final int SKIP_LIMIT = 100;
}
