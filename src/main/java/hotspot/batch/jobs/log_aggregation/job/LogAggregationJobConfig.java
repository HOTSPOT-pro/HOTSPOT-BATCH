package hotspot.batch.jobs.log_aggregation.job;

import hotspot.batch.common.config.JobParameterValidator;
import hotspot.batch.common.listener.JobResultListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogAggregationJobConfig {

    private final JobParameterValidator jobParameterValidator;
    private final JobResultListener jobResultListener;

    public LogAggregationJobConfig(
            JobParameterValidator jobParameterValidator,
            JobResultListener jobResultListener) {
        this.jobParameterValidator = jobParameterValidator;
        this.jobResultListener = jobResultListener;
    }

    @Bean
    public Job logAggregationJob(
            JobRepository jobRepository,
            @Qualifier("prepareSubUsageMonthlyAggregationWindowStep") Step prepareSubUsageMonthlyAggregationWindowStep,
            @Qualifier("subUsageMonthlyAggregationStep") Step subUsageMonthlyAggregationStep,
            @Qualifier("commitSubUsageMonthlyAggregationCursorStep") Step commitSubUsageMonthlyAggregationCursorStep) {
        return new JobBuilder("logAggregationJob", jobRepository)
                .validator(jobParameterValidator)
                .start(prepareSubUsageMonthlyAggregationWindowStep)
                .next(subUsageMonthlyAggregationStep)
                .next(commitSubUsageMonthlyAggregationCursorStep)
                .listener(jobResultListener)
                .build();
    }
}
