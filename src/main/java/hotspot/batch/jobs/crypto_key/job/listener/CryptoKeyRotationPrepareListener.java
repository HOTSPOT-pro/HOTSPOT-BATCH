package hotspot.batch.jobs.crypto_key.job.listener;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.crypto_key.service.CryptoKeyRotationLifecycleService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CryptoKeyRotationPrepareListener implements StepExecutionListener {

    private final CryptoKeyRotationLifecycleService lifecycleService;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        if (hasPreparedContext(stepExecution)) {
            return;
        }

        Integer bucketId = readInt(stepExecution, "targetBucketId");
        var plan = lifecycleService.prepareRotation(bucketId, null);

        var stepContext = stepExecution.getExecutionContext();
        stepContext.putInt("targetBucketId", plan.bucketId());
        stepContext.putInt("sourceKeyVersion", plan.sourceKeyVersion());
        stepContext.putInt("targetKeyVersion", plan.targetKeyVersion());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }

    private boolean hasPreparedContext(StepExecution stepExecution) {
        if (stepExecution.getExecutionContext().containsKey("sourceKeyVersion")
                && stepExecution.getExecutionContext().containsKey("targetKeyVersion")) {
            return true;
        }

        return stepExecution.getJobExecution().getExecutionContext().containsKey("sourceKeyVersion")
                && stepExecution.getJobExecution().getExecutionContext().containsKey("targetKeyVersion");
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
