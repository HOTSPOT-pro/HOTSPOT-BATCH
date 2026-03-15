package hotspot.batch.jobs.llm_feedback.processor;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.llm_feedback.dto.AiFeedback;
import hotspot.batch.jobs.llm_feedback.dto.Feedback;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import hotspot.batch.jobs.llm_feedback.dto.PolicyRecommend;
import hotspot.batch.jobs.llm_feedback.dto.SummaryText;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * WeeklyReport를 기반으로 LLM 피드백을 생성하는 Processor 및 관련 설정
 */
@Slf4j
@Configuration
public class LlmFeedbackProcessor {

    /**
     * 비즈니스 로직을 담당하는 내부 ItemProcessor 구현체
     */
    @Bean
    public ItemProcessor<LlmFeedbackWeeklyReport, LlmFeedbackWeeklyReport> llmFeedbackProcessorDelegate() {
        return item -> {
            log.info("Processing LLM Feedback for weeklyReportId: {}", item.weeklyReportId());
            
            // TODO: 프롬프트 구성 및 LLM API 호출 (Resilience4j 적용)
            AiFeedback dummyFeedback = AiFeedback.builder()
                .summaryText(SummaryText.builder()
                    .overall("이번 주는 평일 대비 주말 사용량이 증가했습니다.")
                    .daily("화요일에 사용량이 가장 크게 증가했습니다.")
                    .hourly("밤 10시부터 새벽 1시 사이 사용 비중이 높습니다.")
                    .category("미디어 카테고리가 65%를 차지합니다.")
                    .build())
                .keyInsights(java.util.List.of("주말 사용 집중", "심야 사용 증가", "미디어 편중"))
                .feedback(Feedback.builder()
                    .toParent("심야 미디어 사용을 조율해보세요.")
                    .toChild("일찍 자자!")
                    .build())
                .policyRecommendList(java.util.List.of(
                    PolicyRecommend.builder()
                        .title("수면 골든타임")
                        .description("거실에 두기")
                        .reason("심야 사용 방지")
                        .build()
                ))
                .build();
                
            String aiModel = "gpt-4-turbo";
            String promptVersion = "v1.0";

            return item.withAiFeedback(dummyFeedback, aiModel, promptVersion);
        };
    }

    /**
     * 비동기 처리를 위한 AsyncItemProcessor 설정
     */
    @Bean
    public AsyncItemProcessor<LlmFeedbackWeeklyReport, LlmFeedbackWeeklyReport> asyncLlmFeedbackProcessor(
            TaskExecutor taskExecutor) {
        AsyncItemProcessor<LlmFeedbackWeeklyReport, LlmFeedbackWeeklyReport> asyncProcessor = 
                new AsyncItemProcessor<>(llmFeedbackProcessorDelegate());
        asyncProcessor.setTaskExecutor(taskExecutor);
        return asyncProcessor;
    }

    /**
     * LLM 호출 전용 스레드 풀
     */
    @Bean
    public TaskExecutor llmFeedbackTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(BatchConstants.POOL_SIZE);
        executor.setMaxPoolSize(BatchConstants.POOL_SIZE);
        executor.setQueueCapacity(BatchConstants.POOL_SIZE);
        executor.setThreadNamePrefix("LlmFeedbackExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
