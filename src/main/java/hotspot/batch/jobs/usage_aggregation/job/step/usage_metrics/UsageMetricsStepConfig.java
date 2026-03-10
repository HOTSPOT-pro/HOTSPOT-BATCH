package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.listener.TimeBasedChunkListener;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsCommand;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.UsageMetricsProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader.UsageMetricsReader;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.writer.UsageMetricsWriter;

/**
 * Step2 chunk 구성을 정의하는 설정
 * Redis 집계 결과를 기반으로 지표, 태그, 점수, 스냅샷 저장 작업을 수행
 */
@Configuration
public class UsageMetricsStepConfig {

    private static final int CHUNK_SIZE = 1000;

    private final TimeBasedChunkListener timeBasedChunkListener;

    public UsageMetricsStepConfig(TimeBasedChunkListener timeBasedChunkListener) {
        this.timeBasedChunkListener = timeBasedChunkListener;
    }

    @Bean
    public Step usageMetricsStep(
            JobRepository jobRepository,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
            @Qualifier("usageMetricsReader") UsageMetricsReader usageMetricsReader,
            @Qualifier("usageMetricsProcessor") UsageMetricsProcessor usageMetricsProcessor,
            @Qualifier("usageMetricsWriter") UsageMetricsWriter usageMetricsWriter) {
        return new StepBuilder("usageMetricsStep", jobRepository)
                .<UsageMetricsItem, UsageMetricsCommand>chunk(CHUNK_SIZE, transactionManager)
                .reader(usageMetricsReader)
                .processor(usageMetricsProcessor)
                .writer(usageMetricsWriter)
                .listener(timeBasedChunkListener)
                .build();
    }
}
