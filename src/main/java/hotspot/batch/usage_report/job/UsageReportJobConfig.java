package hotspot.batch.usage_report.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class UsageReportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(UsageReportJobConfig.class);

    @Bean
    public Job usageReportJob(JobRepository jobRepository, Step usageReportStep) {
        return new JobBuilder("usageReportJob", jobRepository).start(usageReportStep).build();
    }

    @Bean
    public Step usageReportStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("usageReportStep", jobRepository)
                .tasklet(
                        (contribution, chunkContext) -> {
                            log.info("usageReportStep skeleton executed");
                            return RepeatStatus.FINISHED;
                        },
                        transactionManager)
                .build();
    }
}
