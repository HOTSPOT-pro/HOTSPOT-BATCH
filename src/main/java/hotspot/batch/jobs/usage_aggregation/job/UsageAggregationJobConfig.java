package hotspot.batch.jobs.usage_aggregation.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import hotspot.batch.common.config.JobParameterValidator;
import hotspot.batch.common.listener.JobResultListener;

/**
 * Job1 : 사용량 집계 및 연산 Config
 * - Step1 : 대상자 선정 및 WeeklyReport 시드 데이터 생성 (Chunk)
 * - Step2 : Redis 집계 + 지표 / 태그 / 점수 + 스냅샷 저장 (Chunk)
 */
@Configuration
public class UsageAggregationJobConfig {

    private final JobParameterValidator jobParameterValidator;
    private final JobResultListener jobResultListener;

    public UsageAggregationJobConfig(
            JobParameterValidator jobParameterValidator,
            JobResultListener jobResultListener) {
        this.jobParameterValidator = jobParameterValidator;
        this.jobResultListener = jobResultListener;
    }

    @Bean
    public Job usageAggregationJob(
            JobRepository jobRepository,
            @Qualifier("reportSeedStep") Step reportSeedStep,
            @Qualifier("usageMetricsStep") Step usageMetricsStep) {
        return new JobBuilder("usageAggregationJob", jobRepository)
                .validator(jobParameterValidator)
                //.start(reportSeedStep)
                .start(usageMetricsStep)
                .listener(jobResultListener)
                .build();
    }
}
