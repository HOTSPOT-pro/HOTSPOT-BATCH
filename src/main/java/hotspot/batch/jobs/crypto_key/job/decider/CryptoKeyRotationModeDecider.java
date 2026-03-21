package hotspot.batch.jobs.crypto_key.job.decider;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Component
public class CryptoKeyRotationModeDecider implements JobExecutionDecider {

    private static final FlowExecutionStatus MASTER = new FlowExecutionStatus("MASTER");
    private static final FlowExecutionStatus WORKER = new FlowExecutionStatus("WORKER");

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        String targetBucketId = jobExecution.getJobParameters().getString("targetBucketId");
        return (targetBucketId == null || targetBucketId.isBlank()) ? MASTER : WORKER;
    }
}
