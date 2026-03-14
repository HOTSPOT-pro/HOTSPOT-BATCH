package hotspot.batch.jobs.usage_aggregation.job.step.report_seed;

import java.time.LocalDate;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;
import lombok.RequiredArgsConstructor;

/**
 * Job1 - step1 - Processor
 * 읽어온 대상자 정보를 바탕으로 이번 주 리포트 분석 기간을 계산하고 시드 객체를 생성함
 */
@Component
@StepScope
@RequiredArgsConstructor
public class ReportSeedProcessor implements ItemProcessor<ReportSeedInput, WeeklyReport> {

    private final UsageAggregationDateCalculator dateCalculator;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public WeeklyReport process(ReportSeedInput item) {
        LocalDate baseDate = dateCalculator.getBaseDate(targetDate);
        LocalDate weekStartDate = dateCalculator.getWeekStartDate(baseDate);
        LocalDate weekEndDate = dateCalculator.getWeekEndDate(baseDate);

        // 초기 Seed 데이터 생성 (필수 식별 정보 및 분석 기간 설정)
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
