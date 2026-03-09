package hotspot.batch.jobs.crypto_key.job;

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
public class CryptoKeyRotationJobConfig {

    private final JobParameterValidator jobParameterValidator;
    private final JobResultListener jobResultListener;
    private final TimeBasedChunkListener timeBasedChunkListener;

    public CryptoKeyRotationJobConfig(
            JobParameterValidator jobParameterValidator,
            JobResultListener jobResultListener,
            TimeBasedChunkListener timeBasedChunkListener) {
        this.jobParameterValidator = jobParameterValidator;
        this.jobResultListener = jobResultListener;
        this.timeBasedChunkListener = timeBasedChunkListener;
    }

    @Bean
    public Job cryptoKeyRotationJob(JobRepository jobRepository, Step cryptoKeyRotationStep) {
        return new JobBuilder("cryptoKeyRotationJob", jobRepository)
                .validator(jobParameterValidator)
                .start(cryptoKeyRotationStep)
                .listener(jobResultListener)
                .build();
    }

    @Bean
    public Step cryptoKeyRotationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        return new StepBuilder("cryptoKeyRotationStep", jobRepository)
                .<Long, Long>chunk(1000)
                .reader(cryptoKeyRotationReader())
                .processor(cryptoKeyRotationProcessor())
                .writer(cryptoKeyRotationWriter())
                .listener(timeBasedChunkListener)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public ItemReader<Long> cryptoKeyRotationReader() {
        return new ListItemReader<>(List.of());
    }

    @Bean
    public ItemProcessor<Long, Long> cryptoKeyRotationProcessor() {
        return item -> item;
    }

    @Bean
    public ItemWriter<Long> cryptoKeyRotationWriter() {
        return items -> {
            // Skeleton writer for future implementation.
        };
    }
}
