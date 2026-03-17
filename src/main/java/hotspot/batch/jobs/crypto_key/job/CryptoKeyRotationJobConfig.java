package hotspot.batch.jobs.crypto_key.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
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

import hotspot.batch.common.config.JobParameterValidator;
import hotspot.batch.common.listener.JobResultListener;
import hotspot.batch.common.listener.StepResultListener;
import hotspot.batch.common.listener.TimeBasedChunkListener;
import hotspot.batch.jobs.crypto_key.config.CryptoKeyRotationProperties;
import hotspot.batch.jobs.crypto_key.dto.PhoneRotationTarget;
import hotspot.batch.jobs.crypto_key.dto.PhoneRotationUpdate;
import hotspot.batch.jobs.crypto_key.job.tasklet.FinalizeRotationTasklet;
import hotspot.batch.jobs.crypto_key.job.tasklet.PrepareRotationTasklet;

@Configuration
public class CryptoKeyRotationJobConfig {

    private final JobParameterValidator jobParameterValidator;
    private final JobResultListener jobResultListener;
    private final TimeBasedChunkListener timeBasedChunkListener;
    private final StepResultListener stepResultListener;
    private final CryptoKeyRotationProperties properties;

    public CryptoKeyRotationJobConfig(
            JobParameterValidator jobParameterValidator,
            JobResultListener jobResultListener,
            TimeBasedChunkListener timeBasedChunkListener,
            StepResultListener stepResultListener,
            CryptoKeyRotationProperties properties) {
        this.jobParameterValidator = jobParameterValidator;
        this.jobResultListener = jobResultListener;
        this.timeBasedChunkListener = timeBasedChunkListener;
        this.stepResultListener = stepResultListener;
        this.properties = properties;
    }

    @Bean
    public Job cryptoKeyRotationJob(
            JobRepository jobRepository,
            @Qualifier("prepareRotationStep") Step prepareRotationStep,
            @Qualifier("reencryptPhoneStep") Step reencryptPhoneStep,
            @Qualifier("finalizeRotationStep") Step finalizeRotationStep) {
        return new JobBuilder("cryptoKeyRotationJob", jobRepository)
                .validator(jobParameterValidator)
                .start(prepareRotationStep)
                .next(reencryptPhoneStep)
                .next(finalizeRotationStep)
                .listener(jobResultListener)
                .build();
    }

    @Bean
    public Step prepareRotationStep(
            JobRepository jobRepository,
            @Qualifier("mainTransactionManager") PlatformTransactionManager transactionManager,
            PrepareRotationTasklet prepareRotationTasklet) {
        return new StepBuilder("prepareRotationStep", jobRepository)
                .tasklet(prepareRotationTasklet, transactionManager)
                .listener(stepResultListener)
                .build();
    }

    @Bean
    public Step reencryptPhoneStep(
            JobRepository jobRepository,
            @Qualifier("mainTransactionManager") PlatformTransactionManager transactionManager,
            @Qualifier("cryptoKeyRotationReader") JdbcPagingItemReader<PhoneRotationTarget> reader,
            ItemProcessor<PhoneRotationTarget, PhoneRotationUpdate> reencryptPhoneProcessor,
            ItemWriter<PhoneRotationUpdate> reencryptPhoneWriter) {
        return new StepBuilder("reencryptPhoneStep", jobRepository)
                .<PhoneRotationTarget, PhoneRotationUpdate>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(reencryptPhoneProcessor)
                .writer(reencryptPhoneWriter)
                .listener(timeBasedChunkListener)
                .listener(stepResultListener)
                .build();
    }

    @Bean
    public Step finalizeRotationStep(
            JobRepository jobRepository,
            @Qualifier("mainTransactionManager") PlatformTransactionManager transactionManager,
            FinalizeRotationTasklet finalizeRotationTasklet) {
        return new StepBuilder("finalizeRotationStep", jobRepository)
                .tasklet(finalizeRotationTasklet, transactionManager)
                .listener(stepResultListener)
                .build();
    }
}
