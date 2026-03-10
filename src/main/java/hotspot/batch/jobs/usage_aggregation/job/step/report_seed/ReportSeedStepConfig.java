package hotspot.batch.jobs.usage_aggregation.job.step.report_seed;

import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.listener.TimeBasedChunkListener;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.processor.ReportSeedProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.reader.ReportSeedReader;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.writer.ReportSeedWriter;

/**
 * Step1: 대상자를 선정하고 WeeklyReport 초기 row를 생성한다.
 *
 * reportSeedStep:
 * 파티션을 worker step에 분배하는 master step.
 *
 * reportSeedWorkerStep:
 * 각 파티션에서 chunk 단위로 read/process/write를 수행하는 worker step.
 */
@Configuration
public class ReportSeedStepConfig {

    private final TimeBasedChunkListener timeBasedChunkListener;

    public ReportSeedStepConfig(TimeBasedChunkListener timeBasedChunkListener) {
        this.timeBasedChunkListener = timeBasedChunkListener;
    }

    @Bean
    public Step reportSeedStep(
            JobRepository jobRepository,
            Partitioner reportSeedPartitioner,
            PartitionHandler reportSeedPartitionHandler) {
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
                .<Long, Long>chunk(1000, transactionManager)
                .reader(reportSeedReader)
                .processor(reportSeedProcessor)
                .writer(reportSeedWriter)
                .listener(timeBasedChunkListener)
                .build();
    }
}
