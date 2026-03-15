package hotspot.batch.jobs.llm_feedback.processor;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${llm.job.prompt-path}")
    private String promptPath;

    public String createPrompt(LlmFeedbackWeeklyReport report) {
        String template = loadTemplate(promptPath);
        
        // 입력받은 JSON 스펙에 맞춰 데이터를 조합 (scoreData 반영)
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
        return template.replace("{{userDataJson}}", userDataJson);
    }

    private String loadTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("프롬프트 템플릿 로드 실패: " + path, e);
        }
    }
}
