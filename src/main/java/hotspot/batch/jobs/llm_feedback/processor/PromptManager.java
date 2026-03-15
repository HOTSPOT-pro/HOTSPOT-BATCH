package hotspot.batch.jobs.llm_feedback.processor;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.llm_feedback.config.LlmProperties;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import hotspot.batch.jobs.llm_feedback.dto.PromptMessages;
import hotspot.batch.common.exception.LlmPromptTemplateLoadException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 프롬프트 템플릿 로딩 및 데이터 바인딩을 담당
 */
@Component
@RequiredArgsConstructor
public class PromptManager {

    private final JsonConverter jsonConverter;
    private final LlmProperties properties;

    /**
     * 리포트 데이터를 기반으로 시스템/사용자 메시지 쌍을 생성
     */
    public PromptMessages createPromptMessages(LlmFeedbackWeeklyReport report) {
        String fullTemplate = loadTemplate(properties.job().promptPath());
        
        // '---' 구분자를 기준으로 시스템 메시지와 사용자 메시지 템플릿 분리
        String[] parts = fullTemplate.split("---");
        String systemTemplate = parts[0].trim();
        String userTemplate = parts.length > 1 ? parts[1].trim() : "";

        // 사용자 데이터 JSON 변환 및 바인딩
        Map<String, Object> userData = Map.of(
            "subId", report.subId(),
            "name", report.name(),
            "weekStartDate", report.weekStartDate(),
            "weekEndDate", report.weekEndDate(),
            "overview", Map.of(
                "scoreInfo", report.scoreData(),
                "tags", report.tags()
            ),
            "usageDetails", report.summaryData()
        );

        String userDataJson = jsonConverter.toJson(userData);
        String boundUserMessage = userTemplate.replace("{{userDataJson}}", userDataJson);

        return new PromptMessages(systemTemplate, boundUserMessage);
    }

    private String loadTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 범용 RuntimeException 대신 구체적인 도메인 예외 발생
            throw new LlmPromptTemplateLoadException("프롬프트 템플릿 로드 실패", path, e);
        }
    }
}
