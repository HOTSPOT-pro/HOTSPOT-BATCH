package hotspot.batch.jobs.llm_feedback.job.step;

import hotspot.batch.common.listener.StepResultListener;
import hotspot.batch.common.listener.TimeBasedChunkListener;
import hotspot.batch.jobs.llm_feedback.config.LlmProperties;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import hotspot.batch.jobs.llm_feedback.job.step.partition.LlmFeedbackPartitioner;
import hotspot.batch.jobs.llm_feedback.listener.LlmFeedbackSkipListener;
import hotspot.batch.jobs.usage_aggregation.repository.WeeklyReportRepository;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * WeeklyReport LLM 피드백 생성을 위한 Step 설정
 * - UsageMetricsStepConfig 구조와 통일성 유지
 * - Partitioning을 통한 데이터 병렬 읽기 지원
 * - AsyncItemProcessor/Writer를 통한 비동기 LLM 호출 지원
 */
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackStepConfig {

    private final LlmFeedbackSkipListener llmFeedbackSkipListener;
    private final StepResultListener stepResultListener;
    private final TimeBasedChunkListener timeBasedChunkListener;
    private final LlmProperties properties;
    private final WeeklyReportRepository weeklyReportRepository;

    /**
     * [Master Step]
     * 파티셔닝 전략을 통해 Worker Step들에게 작업을 분배한다.
     */
    @Bean
    public Step llmFeedbackStep(
            JobRepository jobRepository,
            @Qualifier("llmFeedbackPartitionHandler") PartitionHandler llmFeedbackPartitionHandler) {
        
        return new StepBuilder("llmFeedbackStep", jobRepository)
                .partitioner("llmFeedbackWorkerStep", llmFeedbackPartitioner())
                .partitionHandler(llmFeedbackPartitionHandler)
                .listener(stepResultListener)
                .build();
    }

    /**
     * [Worker Step]
     * 실제 데이터를 읽고(Reader), 분석하고(Processor), 저장함(Writer)
     */
    @Bean
    public Step llmFeedbackWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("llmFeedbackReader") ItemReader<LlmFeedbackWeeklyReport> reader,
            @Qualifier("asyncLlmFeedbackProcessor") ItemProcessor<LlmFeedbackWeeklyReport, Future<LlmFeedbackWeeklyReport>> processor,
            @Qualifier("asyncLlmFeedbackWriter") ItemWriter<Future<LlmFeedbackWeeklyReport>> writer) {
        
        return new StepBuilder("llmFeedbackWorkerStep", jobRepository)
                .<LlmFeedbackWeeklyReport, Future<LlmFeedbackWeeklyReport>>chunk(properties.job().chunkSize())
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(properties.job().skipLimit())
                .listener(llmFeedbackSkipListener) // Skip 전용 리스너
                .listener(timeBasedChunkListener)  // 진행 상황 모니터링
                .listener(stepResultListener)      // 결과 리스너
                .build();
    }

    /**
     * Master Step이 Worker Step을 병렬 실행하도록 제어하는 Handler
     */
    @Bean
    public PartitionHandler llmFeedbackPartitionHandler(
            @Qualifier("llmFeedbackWorkerStep") Step llmFeedbackWorkerStep,
            @Qualifier("partitionTaskExecutor") TaskExecutor partitionTaskExecutor) {
        
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(llmFeedbackWorkerStep);
        partitionHandler.setTaskExecutor(partitionTaskExecutor);
        partitionHandler.setGridSize(properties.job().gridSize());
        return partitionHandler;
    }

    @Bean
    public LlmFeedbackPartitioner llmFeedbackPartitioner() {
        return new LlmFeedbackPartitioner(weeklyReportRepository);
    }

    /**
     * 파티션들을 병렬로 실행하기 위한 전용 Thread Pool 설정.
     */
    @Bean
    public TaskExecutor partitionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.job().gridSize());
        executor.setMaxPoolSize(properties.job().gridSize());
        executor.setThreadNamePrefix("llm-partition-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
