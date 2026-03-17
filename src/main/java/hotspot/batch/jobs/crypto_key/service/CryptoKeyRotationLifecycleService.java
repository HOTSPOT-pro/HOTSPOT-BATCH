package hotspot.batch.jobs.crypto_key.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hotspot.batch.jobs.crypto_key.dto.SubscriptionKeyVersion;
import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoKeyRotationLifecycleService {

    private final CryptoKeyRotationRepository repository;
    private final PhoneCryptoRotationService phoneCryptoRotationService;

    @Transactional("mainTransactionManager")
    public RotationPlan prepareRotation(int bucketId, Integer sourceKeyVersion) {
        SubscriptionKeyVersion sourceKey = resolveSourceKey(bucketId, sourceKeyVersion);
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

        log.info("Prepared DEK rotation. bucketId={} sourceVersion={} targetVersion={}",
                bucketId, sourceKey.keyVersion(), targetVersion);
        return new RotationPlan(bucketId, sourceKey.keyVersion(), targetVersion);
    }

    @Transactional("mainTransactionManager")
    public void finalizeRotation(int bucketId, int sourceKeyVersion, int targetKeyVersion) {
        long remaining = repository.countSubscriptionsByVersion(bucketId, sourceKeyVersion);
        if (remaining > 0) {
            throw new IllegalStateException(
                    "Rotation is not complete. bucketId=%d sourceKeyVersion=%d remaining=%d"
                            .formatted(bucketId, sourceKeyVersion, remaining));
        }

        repository.updateKeyStatus(bucketId, sourceKeyVersion, "disabled");
        repository.updateKeyStatus(bucketId, targetKeyVersion, "active");

        log.info("Finalized DEK rotation. bucketId={} sourceVersion={} targetVersion={}",
                bucketId, sourceKeyVersion, targetKeyVersion);
    }

    private SubscriptionKeyVersion resolveSourceKey(int bucketId, Integer sourceKeyVersion) {
        if (sourceKeyVersion == null) {
            return repository.findActiveKey(bucketId)
                    .orElseThrow(() -> new IllegalStateException("Active key not found. bucketId=" + bucketId));
        }

        return repository.findKey(bucketId, sourceKeyVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "Source key not found. bucketId=" + bucketId + ", sourceKeyVersion=" + sourceKeyVersion));
    }

    public record RotationPlan(int bucketId, int sourceKeyVersion, int targetKeyVersion) {
    }
}
