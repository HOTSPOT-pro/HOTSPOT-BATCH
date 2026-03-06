package hotspot.batch.family.job;

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
public class FamilyRemoveJobConfig {

    private static final Logger log = LoggerFactory.getLogger(FamilyRemoveJobConfig.class);

    @Bean
    public Job familyRemoveJob(JobRepository jobRepository, Step familyRemoveStep) {
        return new JobBuilder("familyRemoveJob", jobRepository).start(familyRemoveStep).build();
    }

    @Bean
    public Step familyRemoveStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("familyRemoveStep", jobRepository)
                .tasklet(
                        (contribution, chunkContext) -> {
                            log.info("familyRemoveStep skeleton executed");
                            return RepeatStatus.FINISHED;
                        },
                        transactionManager)
                .build();
    }
}
