package hotspot.batch.jobs.crypto_key.job.tasklet;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hotspot.batch.jobs.crypto_key.dto.SubscriptionKeyVersion;
import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import hotspot.batch.jobs.crypto_key.service.PhoneCryptoRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrepareRotationTasklet implements Tasklet {

    private final CryptoKeyRotationRepository repository;
    private final PhoneCryptoRotationService phoneCryptoRotationService;

    @Override
    @Transactional("mainTransactionManager")
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String bucketValue = (String) chunkContext.getStepContext().getJobParameters().get("targetBucketId");
        if (bucketValue == null || bucketValue.isBlank()) {
            throw new IllegalArgumentException("targetBucketId job parameter is required");
        }

        int bucketId = Integer.parseInt(bucketValue);
        String sourceVersionValue = (String) chunkContext.getStepContext().getJobParameters().get("sourceKeyVersion");
        SubscriptionKeyVersion sourceKey = resolveSourceKey(bucketId, sourceVersionValue);
        if (!"active".equalsIgnoreCase(sourceKey.status())) {
            throw new IllegalStateException(
                    "Source key must be active. bucketId=" + bucketId + ", sourceKeyVersion=" + sourceKey.keyVersion());
        }

        int targetVersion = repository.findNextKeyVersion(bucketId);
        var generatedDek = phoneCryptoRotationService.generateNewDek();
        repository.updateKeyStatus(bucketId, sourceKey.keyVersion(), "retired");
        repository.insertKey(
                bucketId,
                targetVersion,
                generatedDek.encryptedDek(),
                generatedDek.kekKeyId(),
                "active");

        var jobExecutionContext = contribution.getStepExecution().getJobExecution().getExecutionContext();
        jobExecutionContext.putInt("targetBucketId", bucketId);
        jobExecutionContext.putInt("sourceKeyVersion", sourceKey.keyVersion());
        jobExecutionContext.putInt("targetKeyVersion", targetVersion);

        log.info("Prepared DEK rotation. bucketId={} sourceVersion={} targetVersion={}",
                bucketId, sourceKey.keyVersion(), targetVersion);
        return RepeatStatus.FINISHED;
    }

    private SubscriptionKeyVersion resolveSourceKey(int bucketId, String sourceVersionValue) {
        if (sourceVersionValue == null || sourceVersionValue.isBlank()) {
            return repository.findActiveKey(bucketId)
                    .orElseThrow(() -> new IllegalStateException("Active key not found. bucketId=" + bucketId));
        }

        int sourceKeyVersion = Integer.parseInt(sourceVersionValue);
        return repository.findKey(bucketId, sourceKeyVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "Source key not found. bucketId=" + bucketId + ", sourceKeyVersion=" + sourceKeyVersion));
    }
}
