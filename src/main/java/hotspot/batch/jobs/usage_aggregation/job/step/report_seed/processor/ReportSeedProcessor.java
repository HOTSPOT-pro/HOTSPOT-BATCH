package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.processor;

import java.time.LocalDate;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;

/**
 * Job1 - step1 - Processor
 * 읽어온 대상자 정보를 바탕으로 이번 주 리포트 분석 기간을 계산하고 시드 객체를 생성함
 */

@Component
@StepScope
public class ReportSeedProcessor implements ItemProcessor<ReportSeedInput, WeeklyReport> {

    private final UsageAggregationDateCalculator dateCalculator;
    private final String targetDate;

    // @RequiredArgsConstructor 대신 명시적 생성자를 작성하여 JobParameter와 의존성을 동시에 안전하게 주입
    public ReportSeedProcessor(
            UsageAggregationDateCalculator dateCalculator,
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        this.dateCalculator = dateCalculator;
        this.targetDate = targetDate;
    }

    @Override
    public WeeklyReport process(ReportSeedInput item) {
        LocalDate baseDate = dateCalculator.getBaseDate(targetDate);
        LocalDate weekStartDate = dateCalculator.getWeekStartDate(baseDate);
        LocalDate weekEndDate = dateCalculator.getWeekEndDate(baseDate);

        return WeeklyReport.builder()
                .familyId(item.familyId())
                .subId(item.subId())
                .name(item.name())
                .weekStartDate(weekStartDate)
                .weekEndDate(weekEndDate)
                .reportStatus(ReportStatus.PENDING.name())
                .build();
    }
}