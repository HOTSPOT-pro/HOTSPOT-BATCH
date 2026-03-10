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
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedItem;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.WeeklyReportSeedCommand;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.partition.ReportSeedPartitioner;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.processor.ReportSeedProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.reader.ReportSeedReader;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.writer.ReportSeedWriter;

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

    @Bean
    public Step reportSeedWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ReportSeedReader reportSeedReader,
            ReportSeedProcessor reportSeedProcessor,
            ReportSeedWriter reportSeedWriter) {
        return new StepBuilder("reportSeedWorkerStep", jobRepository)
                .<ReportSeedItem, WeeklyReportSeedCommand>chunk(CHUNK_SIZE, transactionManager)
                .reader(reportSeedReader)
                .processor(reportSeedProcessor)
                .writer(reportSeedWriter)
                .listener(timeBasedChunkListener)
                .build();
    }

    // [To-Do] 추후 로직 복잡하지면 분리 예정 (partition 범위 계산, retry/skip 등 추가 시)
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

    @Bean
    public ReportSeedPartitioner reportSeedPartitioner() {
        return new ReportSeedPartitioner();
    }

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
