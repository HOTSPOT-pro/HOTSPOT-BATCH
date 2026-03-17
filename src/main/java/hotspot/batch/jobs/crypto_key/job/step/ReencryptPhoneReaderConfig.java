package hotspot.batch.jobs.crypto_key.job.step;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import hotspot.batch.jobs.crypto_key.config.CryptoKeyRotationProperties;
import hotspot.batch.jobs.crypto_key.dto.PhoneRotationTarget;
import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ReencryptPhoneReaderConfig {

    private final CryptoKeyRotationRepository repository;
    private final CryptoKeyRotationProperties properties;

    @Bean
    @StepScope
    public JdbcPagingItemReader<PhoneRotationTarget> cryptoKeyRotationReader(
            @Value("#{jobExecutionContext['targetBucketId']}") Integer bucketId,
            @Value("#{jobExecutionContext['sourceKeyVersion']}") Integer sourceKeyVersion) {
        return repository.buildRotationReader(
                "cryptoKeyRotationReader",
                properties.chunkSize(),
                bucketId,
                sourceKeyVersion);
    }
}
