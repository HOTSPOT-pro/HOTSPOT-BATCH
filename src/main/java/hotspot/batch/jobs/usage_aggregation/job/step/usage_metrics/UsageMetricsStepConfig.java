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
 * [Phase 7] Partitioning 방식으로 원복하되, 전용 스레드 풀 분리를 통해 성능과 안정성 확보
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
                .listener(stepResultListener)
                .build();
    }

    /**
     * Step2 Worker Step: 파티션별로 독립적인 Reader 인스턴스를 사용하여 병렬 I/O 수행
     */
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
     * 파티션 실행을 위한 스레드 풀
     */
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

    /**
     * Reader의 Pre-fetching 비동기 작업을 위한 전용 Thread Pool
     * 파티션 스레드와 분리하여 데드락을 방지하고 병렬 I/O 가속
     */
    @Bean
    public TaskExecutor usageMetricsPreFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 각 파티션이 3개의 비동기 작업을 수행하므로 넉넉하게 설정
        executor.setCorePoolSize(BatchConstants.GRID_SIZE * 4);
        executor.setMaxPoolSize(BatchConstants.GRID_SIZE * 8);
        executor.setThreadNamePrefix("pre-fetch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
