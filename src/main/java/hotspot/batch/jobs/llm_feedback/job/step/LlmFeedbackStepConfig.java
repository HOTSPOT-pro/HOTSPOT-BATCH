package hotspot.batch.jobs.llm_feedback.job.step;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.llm_feedback.config.LlmBatchConstants;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import hotspot.batch.jobs.llm_feedback.listener.LlmFeedbackSkipListener;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * WeeklyReport LLM 피드백 생성을 위한 Step 설정
 */
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackStepConfig {

    private final LlmFeedbackSkipListener llmFeedbackSkipListener;

    @Bean
    public Step llmFeedbackStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("llmFeedbackReader") ItemReader<LlmFeedbackWeeklyReport> reader,
            @Qualifier("asyncLlmFeedbackProcessor") ItemProcessor<LlmFeedbackWeeklyReport, Future<LlmFeedbackWeeklyReport>> processor,
            @Qualifier("asyncLlmFeedbackWriter") ItemWriter<Future<LlmFeedbackWeeklyReport>> writer) {
        
        return new StepBuilder("llmFeedbackStep", jobRepository)
                .<LlmFeedbackWeeklyReport, Future<LlmFeedbackWeeklyReport>>chunk(BatchConstants.LLM_CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(LlmBatchConstants.SKIP_LIMIT)
                .listener(llmFeedbackSkipListener)
                .build();
    }
}
