package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.UsageMetricsProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.writer.UsageMetricsWriter;
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
import hotspot.batch.common.listener.StepResultListener;

/**
 * Step2: 지표 집계 및 스냅샷 생성 Step 설정.
 * [최종 최적화] 모듈러 파티셔닝 + 최적화된 청크 사이즈 적용
 */
@Configuration
public class UsageMetricsStepConfig {

    private final TimeBasedChunkListener timeBasedChunkListener;
    private final StepResultListener stepResultListener;

    public UsageMetricsStepConfig(TimeBasedChunkListener timeBasedChunkListener,
                                  StepResultListener stepResultListener) {
        this.timeBasedChunkListener = timeBasedChunkListener;
        this.stepResultListener = stepResultListener;
    }

    @Bean
    public Step usageMetricsStep(
            JobRepository jobRepository,
            @Qualifier("usageMetricsPartitionHandler") PartitionHandler usageMetricsPartitionHandler,
            WeeklyReportPartitioner weeklyReportPartitioner) {
        
        return new StepBuilder("usageMetricsStep", jobRepository)
                .partitioner("usageMetricsWorkerStep", weeklyReportPartitioner)
                .partitionHandler(usageMetricsPartitionHandler)
                .listener(stepResultListener)
                .build();
    }

    @Bean
    public Step usageMetricsWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            UsageMetricsReader usageMetricsReader,
            UsageMetricsProcessor usageMetricsProcessor,
            UsageMetricsWriter usageMetricsWriter) {
        
        return new StepBuilder("usageMetricsWorkerStep", jobRepository)
                .<UsageMetricsAggregationInput, WeeklyReport>chunk(BatchConstants.CHUNK_SIZE, transactionManager)
                .reader(usageMetricsReader)
                .processor(usageMetricsProcessor)
                .writer(usageMetricsWriter)
                .listener(timeBasedChunkListener)
                .listener(stepResultListener)
                .build();
    }

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

    @Bean
    public TaskExecutor usageMetricsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(BatchConstants.GRID_SIZE);
        executor.setMaxPoolSize(BatchConstants.GRID_SIZE);
        executor.setThreadNamePrefix("usage-part-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
