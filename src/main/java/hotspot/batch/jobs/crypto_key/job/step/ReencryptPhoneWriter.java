package hotspot.batch.jobs.crypto_key.job.step;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.crypto_key.dto.PhoneRotationUpdate;
import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import lombok.RequiredArgsConstructor;

@Component
@StepScope
@RequiredArgsConstructor
public class ReencryptPhoneWriter implements ItemWriter<PhoneRotationUpdate> {

    private final CryptoKeyRotationRepository repository;

    @Value("#{jobExecutionContext['targetBucketId']}")
    private Integer bucketId;

    @Override
    public void write(Chunk<? extends PhoneRotationUpdate> chunk) {
        for (PhoneRotationUpdate item : chunk.getItems()) {
            repository.updateSubscriptionPhone(
                    item.subId(),
                    bucketId,
                    item.phoneEnc(),
                    item.sourceKeyVersion(),
                    item.targetKeyVersion());
        }
    }
}
