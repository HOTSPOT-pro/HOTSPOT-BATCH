package hotspot.batch.jobs.family_remove.tasklet;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
public class FamilyRemoveTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(FamilyRemoveTasklet.class);

    private final Clock kstClock;

    public FamilyRemoveTasklet(Clock kstClock) {
        this.kstClock = kstClock;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("family remove skeleton execute now={}", LocalDateTime.now(kstClock));
        return RepeatStatus.FINISHED;
    }
}
