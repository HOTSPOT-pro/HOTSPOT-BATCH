package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.reader;

import java.util.List;

import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.stereotype.Component;

/**
 * TODO: ReportTarget 기반 reader로 교체한다.
 */
@Component
public class ReportSeedReader extends ListItemReader<Long> {

    public ReportSeedReader() {
        super(List.of());
    }
}
