package hotspot.batch.jobs.llm_feedback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 관련 모든 설정값을 관리하는 프로퍼티 클래스
 * llm-config.yml의 'llm' 프리픽스 하위 설정들과 매핑됨
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    Job job,
    OpenAi openai,
    Client client
) {
    public record Job(
        int chunkSize,
        int skipLimit,
        String promptPath
    ) {}

    public record OpenAi(
        String model,
        String apiKey,
        double temperature,
        int maxTokens,
        String promptVersion,
        String systemMessage
    ) {}

    public record Client(
        int connectTimeoutMillis,
        int readTimeoutMillis,
        int maxConnections,
        int pendingAcquireMaxCount
    ) {}
}
