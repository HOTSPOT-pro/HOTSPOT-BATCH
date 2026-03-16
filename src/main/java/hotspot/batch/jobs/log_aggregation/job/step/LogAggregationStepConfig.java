package hotspot.batch.jobs.log_aggregation.job.step;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.log_aggregation.repository.LogAggregationRepository.UsageAppliedEventLogRow;
import hotspot.batch.jobs.log_aggregation.job.step.commit.CommitLogAggregationCursorTasklet;
import hotspot.batch.jobs.log_aggregation.job.step.prepare.PrepareLogAggregationWindowTasklet;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class LogAggregationStepConfig {

    @Bean
    public Step prepareSubUsageMonthlyAggregationWindowStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
            PrepareLogAggregationWindowTasklet prepareSubUsageMonthlyAggregationWindowTasklet) {
        return new StepBuilder("prepareSubUsageMonthlyAggregationWindowStep", jobRepository)
                .tasklet(prepareSubUsageMonthlyAggregationWindowTasklet, batchTransactionManager)
                .build();
    }

    @Bean
    public Step subUsageMonthlyAggregationStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
            JdbcPagingItemReader<UsageAppliedEventLogRow> usageAppliedEventLogPagingReader,
            ItemProcessor<UsageAppliedEventLogRow, UsageAppliedEventLogRow> usageAppliedEventLogProcessor,
            ItemWriter<UsageAppliedEventLogRow> subUsageMonthlyAggregationWriter) {
        return new StepBuilder("subUsageMonthlyAggregationStep", jobRepository)
                .<UsageAppliedEventLogRow, UsageAppliedEventLogRow>chunk(BatchConstants.CHUNK_SIZE, batchTransactionManager)
                .reader(usageAppliedEventLogPagingReader)
                .processor(usageAppliedEventLogProcessor)
                .writer(subUsageMonthlyAggregationWriter)
                .build();
    }

    @Bean
    public Step commitSubUsageMonthlyAggregationCursorStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
            CommitLogAggregationCursorTasklet commitSubUsageMonthlyAggregationCursorTasklet) {
        return new StepBuilder("commitSubUsageMonthlyAggregationCursorStep", jobRepository)
                .tasklet(commitSubUsageMonthlyAggregationCursorTasklet, batchTransactionManager)
                .build();
    }
}
