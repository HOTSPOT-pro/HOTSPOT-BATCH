package hotspot.batch.jobs.llm_feedback.processor;

import hotspot.batch.jobs.llm_feedback.client.LlmApiClient;
import hotspot.batch.jobs.llm_feedback.config.LlmProperties;
import hotspot.batch.jobs.llm_feedback.dto.AiFeedback;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import hotspot.batch.jobs.llm_feedback.dto.PromptMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.batch.integration.async.AsyncItemProcessor;

/**
 * WeeklyReport를 기반으로 LLM 피드백을 생성하는 Processor 및 비동기 처리 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackProcessor {

    private final LlmApiClient llmApiClient;
    private final PromptManager promptManager;
    private final LlmProperties properties;

    /**
     * 실제 처리 로직을 담당하는 Delegate
     */
    @Bean
    public ItemProcessor<LlmFeedbackWeeklyReport, LlmFeedbackWeeklyReport> llmFeedbackProcessorDelegate() {
        return item -> {
            log.info("Generating AI Feedback for weeklyReportId: {}", item.weeklyReportId());
            
            // 1. 시스템/사용자 메시지 쌍 생성
            PromptMessages messages = promptManager.createPromptMessages(item);
            
            try {
                // 2. LLM API 호출
                AiFeedback aiFeedback = llmApiClient.generateFeedback(messages)
                        .block(); 
                
                if (aiFeedback == null) {
                    log.warn("AI Feedback generation failed for reportId: {}. Item will be filtered out.", item.weeklyReportId());
                    return null;
                }

                return item.withAiFeedback(aiFeedback, properties.openai().model(), properties.openai().promptVersion());
            } catch (Exception e) {
                log.error("Error during LLM processing for reportId: {}. Skipping item.", item.weeklyReportId(), e);
                throw e; 
            }
        };
    }

    /**
     * I/O Bound 작업인 LLM 호출을 별도 스레드에서 병렬 처리하기 위한 Async 래퍼
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
