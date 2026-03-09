package hotspot.batch.jobs.family_remove.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.config.JobParameterValidator;
import hotspot.batch.common.listener.JobResultListener;
import hotspot.batch.jobs.family_remove.tasklet.FamilyRemoveTasklet;

@Configuration
public class FamilyRemoveJobConfig {

    private final JobParameterValidator jobParameterValidator;
    private final JobResultListener jobResultListener;

    public FamilyRemoveJobConfig(JobParameterValidator jobParameterValidator, JobResultListener jobResultListener) {
        this.jobParameterValidator = jobParameterValidator;
        this.jobResultListener = jobResultListener;
    }

    @Bean
    public Job familyRemoveJob(JobRepository jobRepository, Step familyRemoveStep) {
        return new JobBuilder("familyRemoveJob", jobRepository)
                .validator(jobParameterValidator)
                .start(familyRemoveStep)
                .listener(jobResultListener)
                .build();
    }

    @Bean
    public Step familyRemoveStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FamilyRemoveTasklet familyRemoveTasklet) {
        return new StepBuilder("familyRemoveStep", jobRepository)
                .tasklet(familyRemoveTasklet, transactionManager)
                .build();
    }
}
