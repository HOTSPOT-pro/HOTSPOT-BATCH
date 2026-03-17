package hotspot.batch.jobs.crypto_key.job.tasklet;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.crypto_key.service.CryptoKeyRotationLifecycleService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PrepareRotationTasklet implements Tasklet {

    private final CryptoKeyRotationLifecycleService lifecycleService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String bucketValue = (String) chunkContext.getStepContext().getJobParameters().get("targetBucketId");
        if (bucketValue == null || bucketValue.isBlank()) {
            throw new IllegalArgumentException("targetBucketId job parameter is required");
        }

        int bucketId = Integer.parseInt(bucketValue);
        String sourceVersionValue = (String) chunkContext.getStepContext().getJobParameters().get("sourceKeyVersion");
        Integer sourceKeyVersion =
                (sourceVersionValue == null || sourceVersionValue.isBlank()) ? null : Integer.parseInt(sourceVersionValue);
        var plan = lifecycleService.prepareRotation(bucketId, sourceKeyVersion);

        var jobExecutionContext = contribution.getStepExecution().getJobExecution().getExecutionContext();
        jobExecutionContext.putInt("targetBucketId", plan.bucketId());
        jobExecutionContext.putInt("sourceKeyVersion", plan.sourceKeyVersion());
        jobExecutionContext.putInt("targetKeyVersion", plan.targetKeyVersion());
        return RepeatStatus.FINISHED;
    }
}
