package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader;

import java.util.List;

import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.stereotype.Component;

/**
 * TODO: WeeklyReport PENDING 대상 reader로 교체한다.
 */
@Component
public class UsageMetricsReader extends ListItemReader<Long> {

    public UsageMetricsReader() {
        super(List.of());
    }
}
