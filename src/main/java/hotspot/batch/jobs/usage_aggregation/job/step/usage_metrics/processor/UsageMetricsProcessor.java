package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsCommand;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ReportBasicInfo;

/**
 * Step2 대상 정보를 분석용 계산 결과로 변환하는 processor
 */
@Component
public class UsageMetricsProcessor implements ItemProcessor<ReportBasicInfo, UsageMetricsCommand> {

    @Override
    public UsageMetricsCommand process(ReportBasicInfo item) {
        return new UsageMetricsCommand(
                item.weeklyReportId(),
                null,
                null,
                null,
                null);
    }
}
