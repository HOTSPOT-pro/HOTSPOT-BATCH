package hotspot.batch.jobs.crypto_key.job.listener;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.crypto_key.service.CryptoKeyRotationLifecycleService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CryptoKeyRotationFinalizeListener implements StepExecutionListener {

    private final CryptoKeyRotationLifecycleService lifecycleService;

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus().isUnsuccessful()) {
            return stepExecution.getExitStatus();
        }

        Integer bucketId = readInt(stepExecution, "targetBucketId");
        Integer sourceKeyVersion = readInt(stepExecution, "sourceKeyVersion");
        Integer targetKeyVersion = readInt(stepExecution, "targetKeyVersion");

        try {
            lifecycleService.finalizeRotation(bucketId, sourceKeyVersion, targetKeyVersion);
            return stepExecution.getExitStatus();
        } catch (Exception e) {
            stepExecution.addFailureException(e);
            return ExitStatus.FAILED.addExitDescription(e);
        }
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
