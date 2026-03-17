package hotspot.batch.jobs.crypto_key.job.step;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.crypto_key.dto.PhoneRotationTarget;
import hotspot.batch.jobs.crypto_key.dto.PhoneRotationUpdate;
import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import hotspot.batch.jobs.crypto_key.service.PhoneCryptoRotationService;

@Component
@StepScope
public class ReencryptPhoneProcessor
        implements ItemProcessor<PhoneRotationTarget, PhoneRotationUpdate>, StepExecutionListener {

    private final CryptoKeyRotationRepository repository;
    private final PhoneCryptoRotationService phoneCryptoRotationService;
    private int targetKeyVersion;
    private byte[] sourceDek;
    private byte[] targetDek;

    public ReencryptPhoneProcessor(
            CryptoKeyRotationRepository repository,
            PhoneCryptoRotationService phoneCryptoRotationService) {
        this.repository = repository;
        this.phoneCryptoRotationService = phoneCryptoRotationService;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        Integer bucketId = readInt(stepExecution, "targetBucketId");
        Integer sourceKeyVersion = readInt(stepExecution, "sourceKeyVersion");
        this.targetKeyVersion = readInt(stepExecution, "targetKeyVersion");
        this.sourceDek = phoneCryptoRotationService.unwrapDek(
                repository.findKey(bucketId, sourceKeyVersion)
                        .orElseThrow(() -> new IllegalStateException("Source key not found")));
        this.targetDek = phoneCryptoRotationService.unwrapDek(
                repository.findKey(bucketId, targetKeyVersion)
                        .orElseThrow(() -> new IllegalStateException("Target key not found")));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }

    @Override
    public PhoneRotationUpdate process(PhoneRotationTarget item) {
        String decryptedPhone = phoneCryptoRotationService.decryptPhone(item.phoneEnc(), sourceDek);
        String reencryptedPhone = phoneCryptoRotationService.encryptPhone(decryptedPhone, targetDek);
        return new PhoneRotationUpdate(item.subId(), reencryptedPhone, item.phoneKeyVersion(), targetKeyVersion);
    }

    private Integer readInt(StepExecution stepExecution, String key) {
        if (stepExecution.getExecutionContext().containsKey(key)) {
            return stepExecution.getExecutionContext().getInt(key);
        }

        if (stepExecution.getJobExecution().getExecutionContext().containsKey(key)) {
            return stepExecution.getJobExecution().getExecutionContext().getInt(key);
        }

        throw new IllegalStateException("Missing execution context value: " + key);
    }
}
