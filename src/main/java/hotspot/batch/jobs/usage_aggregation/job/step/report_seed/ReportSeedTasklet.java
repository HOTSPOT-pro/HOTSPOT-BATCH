package hotspot.batch.jobs.usage_aggregation.job.step.report_seed;

import java.time.LocalDate;

import org.springframework.batch.core.ExitStatus;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;

import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator;
import hotspot.batch.jobs.usage_aggregation.repository.WeeklyReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Step1: 대상자 선정 및 WeeklyReport seed row 생성 (Tasklet 방식)
 * 100만 건 규모 처리를 위해 단일 SQL(insert-into-select)로 벌크 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportSeedTasklet implements Tasklet {

    private final WeeklyReportRepository weeklyReportRepository;
    private final UsageAggregationDateCalculator dateCalculator;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // Job Parameter에서 targetDate를 읽음 (없으면 오늘 기준)
        String targetDateStr = (String) chunkContext.getStepContext().getJobParameters().get("targetDate");
        LocalDate baseDate = dateCalculator.getBaseDate(targetDateStr);
        
        String receiveDay = dateCalculator.getReceiveDay(baseDate);
        LocalDate weekStartDate = dateCalculator.getWeekStartDate(baseDate);
        LocalDate weekEndDate = dateCalculator.getWeekEndDate(baseDate);

        log.info("START ReportSeedTasklet - baseDate={}, receiveDay={}, weekStartDate={}, weekEndDate={}", 
                baseDate, receiveDay, weekStartDate, weekEndDate);

        int insertedCount = weeklyReportRepository.insertSeedReports(receiveDay, weekStartDate, weekEndDate);

        log.info("END ReportSeedTasklet - insertedCount={}", insertedCount);
        
        contribution.setExitStatus(ExitStatus.COMPLETED);
        return RepeatStatus.FINISHED;
    }
}
