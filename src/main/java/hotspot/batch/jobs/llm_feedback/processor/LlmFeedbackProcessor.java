package hotspot.batch.jobs.llm_feedback.processor;

import hotspot.batch.jobs.llm_feedback.client.LlmApiClient;
import hotspot.batch.jobs.llm_feedback.dto.AiFeedback;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.batch.integration.async.AsyncItemProcessor;

/**
 * WeeklyReport를 기반으로 LLM 피드백을 생성하는 Processor 설정 및 구현
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackProcessor {

    private final LlmApiClient llmApiClient;
    private final PromptManager promptManager;

    /**
     * 비즈니스 로직: 프롬프트 생성 -> API 호출 -> 결과 바인딩
     */
    @Bean
    public ItemProcessor<LlmFeedbackWeeklyReport, LlmFeedbackWeeklyReport> llmFeedbackProcessorDelegate() {
        return item -> {
            log.info("Generating AI Feedback for weeklyReportId: {}", item.weeklyReportId());
            
            // 1. 프롬프트 생성
            String prompt = promptManager.createPrompt(item);
            
            // 2. LLM API 호출
            try {
                AiFeedback aiFeedback = llmApiClient.generateFeedback(prompt)
                        .block(); // 비동기 스레드 풀 내에서 실행되므로 block 가능
                
                if (aiFeedback == null) {
                    log.warn("AI Feedback generation failed for reportId: {}", item.weeklyReportId());
                    return item; // 실패 시 원본 반환 (상태 미변경)
                }

                // 3. 결과 바인딩 및 상태 변경 (COMPLETED)
                return item.withAiFeedback(aiFeedback, "gpt-4-turbo", "v1.0");
            } catch (Exception e) {
                log.error("Error during LLM processing for reportId: {}", item.weeklyReportId(), e);
                return item; // 에러 시 원본 반환 (SkipPolicy에 의해 처리될 수 있음)
            }
        };
    }

    /**
     * 비동기 처리를 위한 AsyncItemProcessor 설정
     */
    @Bean
    public AsyncItemProcessor<LlmFeedbackWeeklyReport, LlmFeedbackWeeklyReport> asyncLlmFeedbackProcessor(
            TaskExecutor llmFeedbackTaskExecutor) {
        AsyncItemProcessor<LlmFeedbackWeeklyReport, LlmFeedbackWeeklyReport> asyncProcessor = 
                new AsyncItemProcessor<>(llmFeedbackProcessorDelegate());
        asyncProcessor.setTaskExecutor(llmFeedbackTaskExecutor);
        return asyncProcessor;
    }
}
