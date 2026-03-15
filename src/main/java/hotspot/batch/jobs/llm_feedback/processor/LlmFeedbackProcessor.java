package hotspot.batch.jobs.llm_feedback.processor;

import hotspot.batch.jobs.llm_feedback.client.LlmApiClient;
import hotspot.batch.jobs.llm_feedback.config.LlmProperties;
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
            
            // 1. 리포트 데이터를 기반으로 최종 프롬프트 문자열 생성
            String prompt = promptManager.createPrompt(item);
            
            try {
                // 2. LLM API 호출 (Async 스레드 내에서 동기 방식으로 결과 대기)
                AiFeedback aiFeedback = llmApiClient.generateFeedback(prompt)
                        .block(); 
                
                if (aiFeedback == null) {
                    log.warn("AI Feedback generation failed for reportId: {}. Item will be filtered out.", item.weeklyReportId());
                    // 실패 시 null을 반환하여 Spring Batch가 이 아이템을 Writer로 넘기지 않도록 필터링함
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
