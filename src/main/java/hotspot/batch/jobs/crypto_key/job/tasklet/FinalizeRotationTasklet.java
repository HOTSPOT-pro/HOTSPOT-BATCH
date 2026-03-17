package hotspot.batch.jobs.crypto_key.job.tasklet;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizeRotationTasklet implements Tasklet {

    private final CryptoKeyRotationRepository repository;

    @Override
    @Transactional("mainTransactionManager")
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        var executionContext = contribution.getStepExecution().getJobExecution().getExecutionContext();
        int bucketId = executionContext.getInt("targetBucketId");
        int sourceKeyVersion = executionContext.getInt("sourceKeyVersion");
        int targetKeyVersion = executionContext.getInt("targetKeyVersion");

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
        return RepeatStatus.FINISHED;
    }
}
