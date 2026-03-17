package hotspot.batch.jobs.crypto_key.job;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.config.JobParameterValidator;
import hotspot.batch.common.listener.JobResultListener;
import hotspot.batch.common.listener.StepResultListener;
import hotspot.batch.common.listener.TimeBasedChunkListener;
import hotspot.batch.jobs.crypto_key.config.CryptoKeyRotationProperties;
import hotspot.batch.jobs.crypto_key.dto.PhoneRotationTarget;
import hotspot.batch.jobs.crypto_key.dto.PhoneRotationUpdate;
import hotspot.batch.jobs.crypto_key.job.decider.CryptoKeyRotationModeDecider;
import hotspot.batch.jobs.crypto_key.job.listener.CryptoKeyRotationFinalizeListener;
import hotspot.batch.jobs.crypto_key.job.partition.CryptoKeyBucketPartitioner;
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
            JobExecutionDecider cryptoKeyRotationModeDecider,
            @Qualifier("masterRotationStep") Step masterRotationStep,
            @Qualifier("prepareRotationStep") Step prepareRotationStep,
            @Qualifier("reencryptPhoneStep") Step reencryptPhoneStep) {
        return new JobBuilder("cryptoKeyRotationJob", jobRepository)
                .validator(jobParameterValidator)
                .listener(jobResultListener)
                .start(cryptoKeyRotationModeDecider)
                .on("MASTER").to(masterRotationStep)
                .from(cryptoKeyRotationModeDecider)
                .on("WORKER").to(prepareRotationStep)
                .next(reencryptPhoneStep)
                .from(masterRotationStep).on("FAILED").fail()
                .from(masterRotationStep).on("*").end()
                .from(prepareRotationStep).on("FAILED").fail()
                .from(reencryptPhoneStep).on("FAILED").fail()
                .from(reencryptPhoneStep).on("*").end()
                .end()
                .build();
    }

    @Bean
    public Step masterRotationStep(
            JobRepository jobRepository,
            @Qualifier("cryptoKeyRotationPartitionHandler") PartitionHandler partitionHandler,
            CryptoKeyBucketPartitioner partitioner) {
        return new StepBuilder("masterRotationStep", jobRepository)
                .partitioner("reencryptPhoneStep", partitioner)
                .partitionHandler(partitionHandler)
                .listener(stepResultListener)
                .build();
    }

    @Bean
    public PartitionHandler cryptoKeyRotationPartitionHandler(
            @Qualifier("reencryptPhoneStep") Step reencryptPhoneStep,
            @Qualifier("cryptoKeyRotationTaskExecutor") TaskExecutor taskExecutor) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(reencryptPhoneStep);
        partitionHandler.setTaskExecutor(taskExecutor);
        partitionHandler.setGridSize(properties.gridSize());
        return partitionHandler;
    }

    @Bean
    public TaskExecutor cryptoKeyRotationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.gridSize());
        executor.setMaxPoolSize(properties.gridSize());
        executor.setThreadNamePrefix("crypto-key-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
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
            JdbcBatchItemWriter<PhoneRotationUpdate> reencryptPhoneWriter,
            CryptoKeyRotationFinalizeListener cryptoKeyRotationFinalizeListener) {
        return new StepBuilder("reencryptPhoneStep", jobRepository)
                .<PhoneRotationTarget, PhoneRotationUpdate>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(reencryptPhoneProcessor)
                .writer(reencryptPhoneWriter)
                .listener(timeBasedChunkListener)
                .listener(cryptoKeyRotationFinalizeListener)
                .listener(stepResultListener)
                .build();
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<PhoneRotationUpdate> reencryptPhoneWriter(
            @Qualifier("mainDataSource") DataSource dataSource,
            @Value("#{stepExecutionContext['targetBucketId'] != null ? stepExecutionContext['targetBucketId'] : jobExecutionContext['targetBucketId']}") Integer bucketId,
            @Value("#{stepExecutionContext['targetKeyVersion'] != null ? stepExecutionContext['targetKeyVersion'] : jobExecutionContext['targetKeyVersion']}") Integer targetKeyVersion) {
        return new JdbcBatchItemWriterBuilder<PhoneRotationUpdate>()
                .dataSource(dataSource)
                .sql("""
                        update subscription
                           set phone_enc = :phoneEnc,
                               phone_key_version = :targetKeyVersion,
                               modified_time = now()
                         where sub_id = :subId
                           and phone_key_bucket_id = :bucketId
                           and phone_key_version = :sourceKeyVersion
                           and is_deleted = false
                        """)
                .itemSqlParameterSourceProvider(item -> new MapSqlParameterSource()
                        .addValue("phoneEnc", item.phoneEnc())
                        .addValue("targetKeyVersion", targetKeyVersion)
                        .addValue("subId", item.subId())
                        .addValue("bucketId", bucketId)
                        .addValue("sourceKeyVersion", item.sourceKeyVersion()))
                .build();
    }
}
