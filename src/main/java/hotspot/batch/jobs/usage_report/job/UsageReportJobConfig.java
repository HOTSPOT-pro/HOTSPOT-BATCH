package hotspot.batch.jobs.usage_report.job;

import java.util.List;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.config.JobParameterValidator;
import hotspot.batch.common.listener.JobResultListener;
import hotspot.batch.common.listener.TimeBasedChunkListener;

@Configuration
public class UsageReportJobConfig {

    private final JobParameterValidator jobParameterValidator;
    private final JobResultListener jobResultListener;
    private final TimeBasedChunkListener timeBasedChunkListener;

    public UsageReportJobConfig(
            JobParameterValidator jobParameterValidator,
            JobResultListener jobResultListener,
            TimeBasedChunkListener timeBasedChunkListener) {
        this.jobParameterValidator = jobParameterValidator;
        this.jobResultListener = jobResultListener;
        this.timeBasedChunkListener = timeBasedChunkListener;
    }

    @Bean
    public Job usageReportJob(JobRepository jobRepository, Step usageReportStep) {
        return new JobBuilder("usageReportJob", jobRepository)
                .validator(jobParameterValidator)
                .start(usageReportStep)
                .listener(jobResultListener)
                .build();
    }

    @Bean
    public Step usageReportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        return new StepBuilder("usageReportStep", jobRepository)
                .<Long, Long>chunk(1000)
                .reader(usageReportReader())
                .processor(usageReportProcessor())
                .writer(usageReportWriter())
                .listener(timeBasedChunkListener)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public ItemReader<Long> usageReportReader() {
        return new ListItemReader<>(List.of());
    }

    @Bean
    public ItemProcessor<Long, Long> usageReportProcessor() {
        return item -> item;
    }

    @Bean
    public ItemWriter<Long> usageReportWriter() {
        return items -> {
            // Skeleton writer for future implementation.
        };
    }
}
