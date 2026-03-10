package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.listener.TimeBasedChunkListener;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.UsageMetricsProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader.UsageMetricsReader;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.writer.UsageMetricsWriter;

/**
 * Step2: Redis 집계를 기반으로 지표, 태그, 점수, 스냅샷을 저장한다.
 */
@Configuration
public class UsageMetricsStepConfig {

    private final TimeBasedChunkListener timeBasedChunkListener;

    public UsageMetricsStepConfig(TimeBasedChunkListener timeBasedChunkListener) {
        this.timeBasedChunkListener = timeBasedChunkListener;
    }

    @Bean
    public Step usageMetricsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            UsageMetricsReader usageMetricsReader,
            UsageMetricsProcessor usageMetricsProcessor,
            UsageMetricsWriter usageMetricsWriter) {
        return new StepBuilder("usageMetricsStep", jobRepository)
                .<Long, Long>chunk(1000, transactionManager)
                .reader(usageMetricsReader)
                .processor(usageMetricsProcessor)
                .writer(usageMetricsWriter)
                .listener(timeBasedChunkListener)
                .build();
    }
}
