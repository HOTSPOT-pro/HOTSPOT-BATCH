package hotspot.batch.jobs.family_remove.tasklet;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import hotspot.batch.jobs.family_remove.repository.FamilyRemoveBatchRepository;
import hotspot.batch.jobs.family_remove.repository.FamilyRemoveBatchRepository.FamilyRemoveScheduleRow;

@Component
public class FamilyRemoveTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(FamilyRemoveTasklet.class);
    private static final long MIN_FAMILY_MEMBER_COUNT_AFTER_REMOVE = 2L;

    private final Clock kstClock;
    private final FamilyRemoveBatchRepository familyRemoveBatchRepository;
    private final TransactionTemplate processTransactionTemplate;
    private final TransactionTemplate failureTransactionTemplate;

    public FamilyRemoveTasklet(
            Clock kstClock,
            FamilyRemoveBatchRepository familyRemoveBatchRepository,
            PlatformTransactionManager transactionManager) {
        this.kstClock = kstClock;
        this.familyRemoveBatchRepository = familyRemoveBatchRepository;

        this.processTransactionTemplate = new TransactionTemplate(transactionManager);
        this.processTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.failureTransactionTemplate = new TransactionTemplate(transactionManager);
        this.failureTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime now = LocalDateTime.now(kstClock);
        String targetDateStr = (String) chunkContext.getStepContext().getJobParameters().get("targetDate");
        LocalDate baseDate = (targetDateStr == null || targetDateStr.isBlank())
                ? now.toLocalDate()
                : LocalDate.parse(targetDateStr);

        List<FamilyRemoveScheduleRow> dueSchedules = familyRemoveBatchRepository.findDueSchedules(baseDate);
        if (dueSchedules.isEmpty()) {
            log.info("No due family remove schedule. baseDate={}", baseDate);
            return RepeatStatus.FINISHED;
        }

        int successCount = 0;
        int failedCount = 0;
        for (FamilyRemoveScheduleRow schedule : dueSchedules) {
            try {
                processTransactionTemplate.executeWithoutResult(status -> processSchedule(schedule));
                successCount++;
            } catch (Exception e) {
                failedCount++;
                log.error("Failed family remove schedule. scheduleId={} familyId={} targetSubId={}",
                        schedule.scheduleId(), schedule.familyId(), schedule.targetSubId(), e);
                markFailedSafely(schedule.scheduleId());
            }
        }

        log.info("Family remove batch completed. baseDate={} total={} success={} failed={}",
                baseDate, dueSchedules.size(), successCount, failedCount);
        return RepeatStatus.FINISHED;
    }

    private void processSchedule(FamilyRemoveScheduleRow schedule) {
        boolean exists = familyRemoveBatchRepository.existsFamilySub(schedule.familyId(), schedule.targetSubId());
        if (exists) {
            long currentMemberCount = familyRemoveBatchRepository.countFamilyMembers(schedule.familyId());
            long memberCountAfterRemove = currentMemberCount - 1;
            if (memberCountAfterRemove < MIN_FAMILY_MEMBER_COUNT_AFTER_REMOVE) {
                throw new IllegalStateException("Family member count after remove must be >= 2");
            }

            familyRemoveBatchRepository.deleteFamilySub(schedule.familyId(), schedule.targetSubId());
            familyRemoveBatchRepository.deletePolicySub(schedule.targetSubId());
            familyRemoveBatchRepository.deleteBlockedServiceSub(schedule.targetSubId());
        }

        long newMemberCount = familyRemoveBatchRepository.countFamilyMembers(schedule.familyId());
        long newFamilyDataAmount = calculateFamilyDataAmount(newMemberCount);
        familyRemoveBatchRepository.updateFamilySummary(schedule.familyId(), (int) newMemberCount, newFamilyDataAmount);
        familyRemoveBatchRepository.updateFamilySubDataLimit(schedule.familyId(), newFamilyDataAmount);
        familyRemoveBatchRepository.markCompleted(schedule.scheduleId());
    }

    private void markFailedSafely(Long scheduleId) {
        try {
            failureTransactionTemplate.executeWithoutResult(status ->
                    familyRemoveBatchRepository.markFailed(scheduleId));
        } catch (Exception failureUpdateException) {
            log.error("Failed to update FAILED status. scheduleId={}", scheduleId, failureUpdateException);
        }
    }

    private long calculateFamilyDataAmount(long familyMemberCount) {
        return familyMemberCount * 5L * 1024L * 1024L;
    }
}
