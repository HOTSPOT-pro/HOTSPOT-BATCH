package hotspot.batch.jobs.usage_aggregation.job.step.report_seed;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.listener.TimeBasedChunkListener;
import hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedItem;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.WeeklyReportSeedCommand;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.partition.ReportSeedPartitioner;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.processor.ReportSeedProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.reader.ReportSeedReader;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.writer.ReportSeedWriter;
import hotspot.batch.jobs.usage_aggregation.repository.ReportTargetRepository;

/**
 * Step1의 master/worker step 구성을 정의하는 설정.
 * 대상자 선정과 WeeklyReport seed 생성 작업을 partition + chunk 방식으로 실행한다.
 */
@Configuration
public class ReportSeedStepConfig {

    private static final int CHUNK_SIZE = 1000;
    private static final int GRID_SIZE = 8;

    private final TimeBasedChunkListener timeBasedChunkListener;

    public ReportSeedStepConfig(TimeBasedChunkListener timeBasedChunkListener) {
        this.timeBasedChunkListener = timeBasedChunkListener;
    }

    /**
     * Step1의 master step
     * 파티션 범위를 계산해 worker step 실행을 분배
     */
    @Bean
    public Step reportSeedStep(
            JobRepository jobRepository,
            @Qualifier("reportSeedPartitioner") Partitioner reportSeedPartitioner,
            @Qualifier("reportSeedPartitionHandler") PartitionHandler reportSeedPartitionHandler) {
        return new StepBuilder("reportSeedStep", jobRepository)
                .partitioner("reportSeedWorkerStep", reportSeedPartitioner)
                .partitionHandler(reportSeedPartitionHandler)
                .build();
    }

    /**
     * Step1의 worker step
     * 파티션별 대상을 chunk 단위로 읽어 WeeklyReport seed row를 생성
     */
    @Bean
    public Step reportSeedWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ReportSeedReader reportSeedReader,
            ReportSeedProcessor reportSeedProcessor,
            ReportSeedWriter reportSeedWriter) {
        return new StepBuilder("reportSeedWorkerStep", jobRepository)
                .<ReportSeedItem, WeeklyReportSeedCommand>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(reportSeedReader)
                .processor(reportSeedProcessor)
                .writer(reportSeedWriter)
                .listener(timeBasedChunkListener)
                .build();
    }

    /**
     * master step이 worker step을 병렬 실행할 때 사용하는 partition handler
     */
    @Bean
    public PartitionHandler reportSeedPartitionHandler(
            @Qualifier("reportSeedWorkerStep") Step reportSeedWorkerStep,
            @Qualifier("reportSeedTaskExecutor") TaskExecutor reportSeedTaskExecutor) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setGridSize(GRID_SIZE);
        partitionHandler.setStep(reportSeedWorkerStep);
        partitionHandler.setTaskExecutor(reportSeedTaskExecutor);
        return partitionHandler;
    }

    /**
     * Step1 대상자를 id 범위 기준으로 나누는 partitioner
     */
    @Bean
    public ReportSeedPartitioner reportSeedPartitioner(
            ReportTargetRepository reportTargetRepository,
            UsageAggregationDateCalculator dateCalculator) {
        return new ReportSeedPartitioner(reportTargetRepository, dateCalculator);
    }

    /**
     * worker step 병렬 처리를 위한 전용 task executor
     */
    @Bean
    public TaskExecutor reportSeedTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setThreadNamePrefix("report-seed-");
        taskExecutor.setCorePoolSize(GRID_SIZE);
        taskExecutor.setMaxPoolSize(GRID_SIZE);
        taskExecutor.setQueueCapacity(GRID_SIZE);
        taskExecutor.initialize();
        return taskExecutor;
    }
}
