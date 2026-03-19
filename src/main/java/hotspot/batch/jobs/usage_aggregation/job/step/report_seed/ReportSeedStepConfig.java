package hotspot.batch.jobs.usage_aggregation.job.step.report_seed;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;
import hotspot.batch.common.listener.StepResultListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Step1: 리포트 생성 대상자 선정 및 Seed 데이터 생성 (Multi-threaded Chunk 방식)
 * - Phase 1 성능 개선: AsyncTaskExecutor 도입을 통한 병렬 처리
 */
@Slf4j
@Configuration
public class ReportSeedStepConfig {

    @Bean
    public Step reportSeedStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
            JdbcPagingItemReader<ReportSeedInput> reportSeedReader,
            ItemProcessor<ReportSeedInput, WeeklyReport> reportSeedProcessor,
            JdbcBatchItemWriter<WeeklyReport> reportSeedWriter,
            StepResultListener stepResultListener,
            @Qualifier("reportSeedTaskExecutor") AsyncTaskExecutor taskExecutor) {

        log.info("[ReportSeed-Optimized] Initializing Multi-threaded Step1 with Chunk Size: {}, Thread Pool: {}", 
                 BatchConstants.CHUNK_SIZE, BatchConstants.GRID_SIZE);

        return new StepBuilder("reportSeedStep", jobRepository)
                .<ReportSeedInput, WeeklyReport>chunk(BatchConstants.CHUNK_SIZE)
                .transactionManager(batchTransactionManager)
                .reader(reportSeedReader)
                .processor(reportSeedProcessor)
                .writer(reportSeedWriter)
                .taskExecutor(taskExecutor)
                .listener(stepResultListener)
                .build();
    }

    /**
     * Step1 전용 Thread Pool 설정
     */
    @Bean
    public AsyncTaskExecutor reportSeedTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(BatchConstants.GRID_SIZE);
        executor.setMaxPoolSize(BatchConstants.GRID_SIZE);
        executor.setThreadNamePrefix("seed-thread-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
