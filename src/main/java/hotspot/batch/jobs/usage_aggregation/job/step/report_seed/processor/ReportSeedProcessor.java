package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * TODO: ReportTargetRow -> WeeklyReportCreateCommand 변환 로직으로 교체한다.
 */
@Component
public class ReportSeedProcessor implements ItemProcessor<Long, Long> {

    @Override
    public Long process(Long item) {
        return item;
    }
}
