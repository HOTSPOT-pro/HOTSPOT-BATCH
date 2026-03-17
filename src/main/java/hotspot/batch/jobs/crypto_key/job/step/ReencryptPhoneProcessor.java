package hotspot.batch.jobs.crypto_key.job.step;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.crypto_key.dto.PhoneRotationTarget;
import hotspot.batch.jobs.crypto_key.dto.PhoneRotationUpdate;
import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import hotspot.batch.jobs.crypto_key.service.PhoneCryptoRotationService;

@Component
@StepScope
public class ReencryptPhoneProcessor implements ItemProcessor<PhoneRotationTarget, PhoneRotationUpdate> {

    private final int targetKeyVersion;
    private final byte[] sourceDek;
    private final byte[] targetDek;
    private final PhoneCryptoRotationService phoneCryptoRotationService;

    public ReencryptPhoneProcessor(
            CryptoKeyRotationRepository repository,
            PhoneCryptoRotationService phoneCryptoRotationService,
            @Value("#{stepExecutionContext['targetBucketId'] != null ? stepExecutionContext['targetBucketId'] : jobExecutionContext['targetBucketId']}") Integer bucketId,
            @Value("#{stepExecutionContext['sourceKeyVersion'] != null ? stepExecutionContext['sourceKeyVersion'] : jobExecutionContext['sourceKeyVersion']}") Integer sourceKeyVersion,
            @Value("#{stepExecutionContext['targetKeyVersion'] != null ? stepExecutionContext['targetKeyVersion'] : jobExecutionContext['targetKeyVersion']}") Integer targetKeyVersion) {
        this.targetKeyVersion = targetKeyVersion;
        this.phoneCryptoRotationService = phoneCryptoRotationService;
        this.sourceDek = phoneCryptoRotationService.unwrapDek(
                repository.findKey(bucketId, sourceKeyVersion)
                        .orElseThrow(() -> new IllegalStateException("Source key not found")));
        this.targetDek = phoneCryptoRotationService.unwrapDek(
                repository.findKey(bucketId, targetKeyVersion)
                        .orElseThrow(() -> new IllegalStateException("Target key not found")));
    }

    @Override
    public PhoneRotationUpdate process(PhoneRotationTarget item) {
        String decryptedPhone = phoneCryptoRotationService.decryptPhone(item.phoneEnc(), sourceDek);
        String reencryptedPhone = phoneCryptoRotationService.encryptPhone(decryptedPhone, targetDek);
        return new PhoneRotationUpdate(item.subId(), reencryptedPhone, item.phoneKeyVersion(), targetKeyVersion);
    }
}
