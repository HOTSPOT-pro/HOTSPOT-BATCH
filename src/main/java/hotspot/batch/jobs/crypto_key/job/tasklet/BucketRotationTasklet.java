package hotspot.batch.jobs.crypto_key.job.tasklet;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hotspot.batch.jobs.crypto_key.config.CryptoKeyRotationProperties;
import hotspot.batch.jobs.crypto_key.dto.PhoneRotationTarget;
import hotspot.batch.jobs.crypto_key.dto.SubscriptionKeyVersion;
import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import hotspot.batch.jobs.crypto_key.service.PhoneCryptoRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class BucketRotationTasklet implements Tasklet {

    private final CryptoKeyRotationRepository repository;
    private final PhoneCryptoRotationService phoneCryptoRotationService;
    private final CryptoKeyRotationProperties properties;

    @Override
    @Transactional("mainTransactionManager")
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Integer targetBucketId = chunkContext.getStepContext().getStepExecutionContext().containsKey("targetBucketId")
                ? (Integer) chunkContext.getStepContext().getStepExecutionContext().get("targetBucketId")
                : null;
        if (targetBucketId == null) {
            throw new IllegalArgumentException("targetBucketId step execution context is required");
        }

        SubscriptionKeyVersion sourceKey = repository.findActiveKey(targetBucketId)
                .orElseThrow(() -> new IllegalStateException("Active key not found. bucketId=" + targetBucketId));

        if (!"active".equalsIgnoreCase(sourceKey.status())) {
            throw new IllegalStateException(
                    "Source key must be active. bucketId=" + targetBucketId + ", sourceKeyVersion=" + sourceKey.keyVersion());
        }

        int targetVersion = repository.findNextKeyVersion(targetBucketId);
        var generatedDek = phoneCryptoRotationService.generateNewDek();
        repository.updateKeyStatus(targetBucketId, sourceKey.keyVersion(), "retired");
        repository.insertKey(
                targetBucketId,
                targetVersion,
                generatedDek.encryptedDek(),
                generatedDek.kekKeyId(),
                "active");

        byte[] sourceDek = phoneCryptoRotationService.unwrapDek(sourceKey);
        byte[] targetDek = generatedDek.plainDek();

        JdbcPagingItemReader<PhoneRotationTarget> reader = repository.buildRotationReader(
                "cryptoKeyBucketRotationReader-" + targetBucketId,
                properties.chunkSize(),
                targetBucketId,
                sourceKey.keyVersion());

        int processedCount = 0;
        reader.open(new ExecutionContext());
        try {
            PhoneRotationTarget item;
            while ((item = reader.read()) != null) {
                String decryptedPhone = phoneCryptoRotationService.decryptPhone(item.phoneEnc(), sourceDek);
                String reencryptedPhone = phoneCryptoRotationService.encryptPhone(decryptedPhone, targetDek);
                repository.updateSubscriptionPhone(
                        item.subId(),
                        targetBucketId,
                        reencryptedPhone,
                        sourceKey.keyVersion(),
                        targetVersion);
                processedCount++;
            }
        } finally {
            reader.close();
        }

        long remaining = repository.countSubscriptionsByVersion(targetBucketId, sourceKey.keyVersion());
        if (remaining > 0) {
            throw new IllegalStateException(
                    "Rotation is not complete. bucketId=%d sourceKeyVersion=%d remaining=%d"
                            .formatted(targetBucketId, sourceKey.keyVersion(), remaining));
        }

        repository.updateKeyStatus(targetBucketId, sourceKey.keyVersion(), "disabled");
        repository.updateKeyStatus(targetBucketId, targetVersion, "active");

        log.info("Completed partitioned DEK rotation. bucketId={} sourceVersion={} targetVersion={} processedCount={}",
                targetBucketId, sourceKey.keyVersion(), targetVersion, processedCount);
        return RepeatStatus.FINISHED;
    }
}
