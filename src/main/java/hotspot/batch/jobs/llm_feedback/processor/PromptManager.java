package hotspot.batch.jobs.llm_feedback.processor;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.llm_feedback.config.LlmBatchConstants;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
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

    /**
     * 리포트 데이터를 기반으로 최종 프롬프트를 생성
     */
    public String createPrompt(LlmFeedbackWeeklyReport report) {
        String template = loadTemplate(LlmBatchConstants.PROMPT_TEMPLATE_PATH); // 상수 활용
        
        // 입력받은 JSON 스펙에 맞춰 데이터를 조합 (SummaryData와 ScoreResult 활용)
        Map<String, Object> userData = Map.of(
            "subId", report.subId(),
            "name", report.name(),
            "weekStartDate", report.weekStartDate(),
            "weekEndDate", report.weekEndDate(),
            "overview", Map.of(
                "scoreInfo", report.scoreResult(),
                "tags", report.tags()
            ),
            "usageDetails", report.summaryData() // 제공해주신 복잡한 사용량 데이터가 담겨있음
        );

        String userDataJson = jsonConverter.toJson(userData);
        
        // 템플릿의 변수 치환 (간단한 Replace 방식)
        return template.replace("{{userDataJson}}", userDataJson);
    }

    /**
     * 프롬프트 템플릿 로딩
     */
    private String loadTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("프롬프트 템플릿 로드 실패: " + path, e);
        }
    }
}
