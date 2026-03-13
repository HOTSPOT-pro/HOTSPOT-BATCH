package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics;


import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.common.listener.TimeBasedChunkListener;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.partition.WeeklyReportPartitioner;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader.UsageMetricsReader;

/**
 * Step2: 지표 집계 및 스냅샷 생성 Step 설정.
 */
@Configuration
public class UsageMetricsStepConfig {

    private final TimeBasedChunkListener timeBasedChunkListener;

    public UsageMetricsStepConfig(TimeBasedChunkListener timeBasedChunkListener) {
        this.timeBasedChunkListener = timeBasedChunkListener;
    }

    /**
     * Step2 Master Step: 파티셔닝 전략을 통해 Worker Step들에게 작업을 분배한다.
     */
    @Bean
    public Step usageMetricsStep(
            JobRepository jobRepository,
            @Qualifier("usageMetricsPartitionHandler") PartitionHandler usageMetricsPartitionHandler,
            WeeklyReportPartitioner weeklyReportPartitioner) {
        
        return new StepBuilder("usageMetricsStep", jobRepository)
                .partitioner("usageMetricsWorkerStep", weeklyReportPartitioner)
                .partitionHandler(usageMetricsPartitionHandler)
                .build();
    }

    /**
     * Step2 Worker Step: 실제 데이터를 읽고 처리하는 핵심 단계.
     * Spring Batch 5.x에서는 chunk(int, PlatformTransactionManager) 형식을 권장함.
     */
    @Bean
    public Step usageMetricsWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            UsageMetricsReader usageMetricsReader) {
        
        return new StepBuilder("usageMetricsWorkerStep", jobRepository)
                .<UsageMetricsAggregationInput, UsageMetricsAggregationInput>chunk(BatchConstants.CHUNK_SIZE, transactionManager)
                .reader(usageMetricsReader)
                // .processor(usageMetricsProcessor) // TODO: 구현 후 연결
                // .writer(usageMetricsWriter)       // TODO: 구현 후 연결
                .listener(timeBasedChunkListener)
                .build();
    }

    /**
     * Master Step이 Worker Step을 병렬 실행하도록 제어하는 Handler.
     */
    @Bean
    public PartitionHandler usageMetricsPartitionHandler(
            @Qualifier("usageMetricsWorkerStep") Step usageMetricsWorkerStep,
            @Qualifier("usageMetricsTaskExecutor") TaskExecutor usageMetricsTaskExecutor) {
        
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(usageMetricsWorkerStep);
        partitionHandler.setTaskExecutor(usageMetricsTaskExecutor);
        partitionHandler.setGridSize(BatchConstants.GRID_SIZE);
        return partitionHandler;
    }

    /**
     * Worker Step 병렬 처리를 위한 전용 Thread Pool 설정.
     */
    @Bean
    public TaskExecutor usageMetricsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(BatchConstants.GRID_SIZE);
        executor.setMaxPoolSize(BatchConstants.GRID_SIZE);
        executor.setThreadNamePrefix("usage-metrics-");
        executor.initialize();
        return executor;
    }
}
