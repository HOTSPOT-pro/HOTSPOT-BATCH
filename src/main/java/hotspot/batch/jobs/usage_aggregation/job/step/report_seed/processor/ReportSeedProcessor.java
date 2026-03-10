package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedItem;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.WeeklyReportSeedCommand;

/**
 * Step1 대상자 정보를 WeeklyReport seed 생성 명령으로 변환하는 processor
 */
@Component
public class ReportSeedProcessor implements ItemProcessor<ReportSeedItem, WeeklyReportSeedCommand> {

    @Override
    public WeeklyReportSeedCommand process(ReportSeedItem item) {
        return new WeeklyReportSeedCommand(
                item.subId(),
                null,
                null,
                null,
                "PENDING");
    }
}
